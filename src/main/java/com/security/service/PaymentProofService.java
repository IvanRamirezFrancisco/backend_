package com.security.service;

import com.security.dto.response.PaymentProofResponse;
import com.security.entity.Order;
import com.security.entity.Payment;
import com.security.entity.PaymentProof;
import com.security.entity.User;
import com.security.enums.OrderStatus;
import com.security.enums.PaymentMethod;
import com.security.enums.PaymentProofStatus;
import com.security.enums.PaymentStatus;
import com.security.repository.OrderRepository;
import com.security.repository.PaymentProofRepository;
import com.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentProofService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentProofService.class);

    private final PaymentProofRepository paymentProofRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // Inyectados para Fase 7A: registrar Payment y eventos al gestionar comprobantes
    private final PaymentService paymentService;
    private final PaymentEventService paymentEventService;

    @Value("${upload.payment-proofs.path:uploads/payment-proofs}")
    private String uploadPath;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    @Transactional
    public PaymentProofResponse uploadPaymentProof(
            Long userId, Long orderId, MultipartFile file, String referenceNumber, 
            String bankName, BigDecimal amountDeclared, LocalDate transferDate, String notes) {
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
                
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para subir comprobante a este pedido");
        }

        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new RuntimeException("Este pedido no es de tipo transferencia bancaria");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("No puedes subir un comprobante para un pedido cancelado");
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Este pedido ya ha sido pagado");
        }

        if (paymentProofRepository.existsByOrderIdAndStatus(orderId, PaymentProofStatus.PENDING_REVIEW)) {
            throw new RuntimeException("Ya existe un comprobante pendiente de revisión para este pedido");
        }

        if (paymentProofRepository.existsByOrderIdAndStatus(orderId, PaymentProofStatus.APPROVED)) {
            throw new RuntimeException("Este pedido ya cuenta con un comprobante aprobado");
        }

        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        ValidationResult validation = validateFile(file);
        
        String newFilename = UUID.randomUUID().toString() + validation.extension;
        Path uploadDir = Paths.get(uploadPath).normalize().toAbsolutePath();
        
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
        } catch (IOException e) {
            log.error("Error creando directorio de comprobantes: {}", e.getMessage());
            throw new RuntimeException("Error interno al preparar almacenamiento");
        }

        Path targetLocation = uploadDir.resolve(newFilename).normalize();
        
        if (!targetLocation.getParent().equals(uploadDir)) {
            throw new RuntimeException("Directorio de destino inválido (Path traversal detectado)");
        }

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error guardando comprobante físico: {}", e.getMessage());
            throw new RuntimeException("Error al guardar el archivo");
        }

        try {
            PaymentProof proof = new PaymentProof();
            proof.setOrder(order);
            proof.setUploadedBy(uploader);
            proof.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
            proof.setStoredFilename(newFilename);
            proof.setStoragePath(uploadPath); // Relative
            proof.setContentType(validation.mimeType);
            proof.setFileSizeBytes(file.getSize());
            proof.setReferenceNumber(referenceNumber);
            proof.setBankName(bankName);
            proof.setAmountDeclared(amountDeclared);
            proof.setTransferDate(transferDate);
            proof.setNotes(notes);
            proof.setStatus(PaymentProofStatus.PENDING_REVIEW);

            PaymentProof saved = paymentProofRepository.save(proof);

            // Fase 7A: Garantizar que exista un Payment BANK_TRANSFER PENDING en sales.payments
            // Esto NO cambia el comportamiento visible del comprobante.
            try {
                Payment payment = paymentService.ensureBankTransferPaymentForOrder(order, userId);
                paymentEventService.recordInternalEvent(
                        payment, order, com.security.enums.PaymentProvider.BANK_TRANSFER,
                        PaymentEventService.EVT_PROOF_UPLOADED,
                        "Comprobante #" + saved.getId() + " subido por userId=" + userId);
            } catch (Exception evtEx) {
                log.warn("[PaymentProofService] No se pudo registrar Payment/evento al subir comprobante: {}",
                        evtEx.getMessage());
            }

            return mapToResponse(saved);
        } catch (Exception e) {
            log.error("Error guardando metadata del comprobante, intentando borrar archivo físico. Error: {}", e.getMessage());
            try {
                Files.deleteIfExists(targetLocation);
            } catch (IOException ex) {
                log.error("Fallo al borrar archivo huérfano: {}", targetLocation, ex);
            }
            throw new RuntimeException("Error al procesar el comprobante");
        }
    }

    @Transactional(readOnly = true)
    public PaymentProofResponse getMyPaymentProof(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
                
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para ver este comprobante");
        }
        
        PaymentProof proof = paymentProofRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado para este pedido"));
                
        return mapToResponse(proof);
    }

    @Transactional(readOnly = true)
    public PaymentProofResponse getAdminPaymentProof(Long orderId) {
        PaymentProof proof = paymentProofRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado para este pedido"));
                
        return mapToResponse(proof);
    }

    @Transactional
    public PaymentProofResponse approvePaymentProof(Long adminId, Long orderId) {
        PaymentProof proof = paymentProofRepository.findByOrderIdAndStatus(orderId, PaymentProofStatus.PENDING_REVIEW)
                .orElseThrow(() -> new RuntimeException("No existe un comprobante pendiente de revisión para este pedido"));

        Order order = proof.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("No se puede aprobar el comprobante de un pedido cancelado");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("El pedido ya está pagado");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        proof.setStatus(PaymentProofStatus.APPROVED);
        proof.setReviewedBy(admin);
        proof.setReviewedAt(LocalDateTime.now());

        // Fase 7A: Delegar la actualización de orders.payment_status a PaymentService.
        // markBankTransferAsPaidForOrder garantiza un Payment en PAID y sincroniza
        // orders.payment_status = PAID y orders.transaction_id.
        // Se elimina el update manual de order.paymentStatus para evitar duplicación.
        Long uploadedByUserId = proof.getUploadedBy() != null ? proof.getUploadedBy().getId() : adminId;
        try {
            paymentService.markBankTransferAsPaidForOrder(order, uploadedByUserId, adminId);
            paymentEventService.recordInternalEvent(
                    null, order, com.security.enums.PaymentProvider.BANK_TRANSFER,
                    PaymentEventService.EVT_PROOF_APPROVED,
                    "Comprobante aprobado por adminId=" + adminId);
        } catch (Exception payEx) {
            // Si el servicio de pagos falla, continuar con el flujo original de comprobante
            // para no romper la aprobación. El payment_status se sincronizará luego.
            log.warn("[PaymentProofService] No se pudo marcar Payment como PAID al aprobar comprobante: {}",
                    payEx.getMessage());
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
        }

        PaymentProof saved = paymentProofRepository.save(proof);
        return mapToResponse(saved);
    }

    @Transactional
    public PaymentProofResponse rejectPaymentProof(Long adminId, Long orderId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("El motivo de rechazo es obligatorio");
        }

        PaymentProof proof = paymentProofRepository.findByOrderIdAndStatus(orderId, PaymentProofStatus.PENDING_REVIEW)
                .orElseThrow(() -> new RuntimeException("No existe un comprobante pendiente de revisión para este pedido"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        proof.setStatus(PaymentProofStatus.REJECTED);
        proof.setReviewedBy(admin);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setRejectionReason(reason);

        PaymentProof saved = paymentProofRepository.save(proof);

        // Fase 7A: Registrar evento de rechazo. El Payment queda en PENDING
        // para que el cliente pueda subir un nuevo comprobante.
        try {
            Order order = proof.getOrder();
            paymentEventService.recordInternalEvent(
                    null, order, com.security.enums.PaymentProvider.BANK_TRANSFER,
                    PaymentEventService.EVT_PROOF_REJECTED,
                    "Comprobante rechazado por adminId=" + adminId + " | motivo=" + reason);
        } catch (Exception evtEx) {
            log.warn("[PaymentProofService] No se pudo registrar evento de rechazo: {}", evtEx.getMessage());
        }

        return mapToResponse(saved);
    }
    @Transactional(readOnly = true)
    public com.security.dto.response.PaymentProofFileResponse getMyPaymentProofFile(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
                
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para ver este comprobante");
        }
        
        PaymentProof proof = paymentProofRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado para este pedido"));
                
        return resolveFileResponse(proof);
    }
    
    @Transactional(readOnly = true)
    public com.security.dto.response.PaymentProofFileResponse getAdminPaymentProofFile(Long orderId) {
        PaymentProof proof = paymentProofRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado para este pedido"));
                
        return resolveFileResponse(proof);
    }

    private com.security.dto.response.PaymentProofFileResponse resolveFileResponse(PaymentProof proof) {
        try {
            // Whitelist estricta de seguridad al servir archivo
            String contentType = proof.getContentType();
            if (contentType == null || (!contentType.equals("application/pdf") && 
                !contentType.equals("image/jpeg") && !contentType.equals("image/png") && 
                !contentType.equals("image/webp"))) {
                throw new RuntimeException("Tipo de archivo no permitido por seguridad");
            }

            Path baseDir = Paths.get(proof.getStoragePath()).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(proof.getStoredFilename()).normalize();
            
            // Path traversal protection
            if (!filePath.startsWith(baseDir)) {
                throw new RuntimeException("Ruta de archivo inválida");
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return new com.security.dto.response.PaymentProofFileResponse(
                        resource, 
                        contentType, 
                        proof.getOriginalFilename(),
                        proof.getOrder().getOrderNumber()
                );
            } else {
                throw new RuntimeException("El archivo físico del comprobante no existe");
            }
        } catch (Exception e) {
            log.error("Error al cargar archivo del comprobante para orden {}: {}", proof.getOrder().getId(), e.getMessage());
            throw new RuntimeException("No se pudo leer el archivo del comprobante");
        }
    }

    private ValidationResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("El archivo está vacío");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("El archivo supera el tamaño máximo permitido de 5 MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "";
        String extension = "";
        int i = originalName.lastIndexOf('.');
        if (i > 0) {
            extension = originalName.substring(i + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("Extensión no permitida. Use JPG, PNG o PDF.");
        }
        
        if (extension.equals("pdf")) {
            // Basic PDF magic number check
            try (InputStream is = file.getInputStream()) {
                byte[] header = new byte[4];
                if (is.read(header) == 4 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                    return new ValidationResult("application/pdf", ".pdf");
                } else {
                    throw new RuntimeException("El archivo no es un PDF válido");
                }
            } catch (IOException e) {
                throw new RuntimeException("Error leyendo archivo PDF");
            }
        } else {
            // Image validation via ImageIO
            ImageReader reader = null;
            try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file.getInputStream())) {
                if (imageInputStream == null) throw new RuntimeException("El archivo no es una imagen válida");

                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
                if (!readers.hasNext()) throw new RuntimeException("El archivo no es una imagen válida");

                reader = readers.next();
                String formatName = reader.getFormatName().toLowerCase();
                if (formatName.equals("jpg")) formatName = "jpeg";

                if (!formatName.equals("jpeg") && !formatName.equals("png")) {
                    throw new RuntimeException("Solo se permiten imágenes JPEG o PNG");
                }
                
                String mimeType = formatName.equals("png") ? "image/png" : "image/jpeg";
                String validExt = formatName.equals("png") ? ".png" : ".jpg";
                
                return new ValidationResult(mimeType, validExt);
            } catch (IOException ex) {
                throw new RuntimeException("Error validando la imagen");
            } finally {
                if (reader != null) {
                    reader.dispose();
                }
            }
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "comprobante";
        return filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    private PaymentProofResponse mapToResponse(PaymentProof proof) {
        return PaymentProofResponse.builder()
                .id(proof.getId())
                .orderId(proof.getOrder().getId())
                .orderNumber(proof.getOrder().getOrderNumber())
                .originalFilename(proof.getOriginalFilename())
                .contentType(proof.getContentType())
                .fileSizeBytes(proof.getFileSizeBytes())
                .status(proof.getStatus())
                .referenceNumber(proof.getReferenceNumber())
                .bankName(proof.getBankName())
                .amountDeclared(proof.getAmountDeclared())
                .transferDate(proof.getTransferDate())
                .notes(proof.getNotes())
                .uploadedAt(proof.getCreatedAt())
                .reviewedAt(proof.getReviewedAt())
                .rejectionReason(proof.getRejectionReason())
                .build();
    }
    
    private static class ValidationResult {
        public final String mimeType;
        public final String extension;
        public ValidationResult(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }
}
