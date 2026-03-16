package com.security.service;

import com.security.entity.Product;
import com.security.entity.User;
import com.security.repository.ProductRepository;
import com.security.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servicio de exportación a CSV para Productos y Usuarios.
 *
 * <p>
 * Solo exporta columnas útiles para un humano. Se omiten contraseñas,
 * tokens, hashes y fechas internas no relevantes.
 * </p>
 *
 * <p>
 * Formato de salida: UTF-8 con BOM (para compatibilidad con Excel en Windows),
 * valores separados por comas, cadenas con comillas dobles si contienen coma o
 * salto de línea.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class CsvExportService {

    private static final Logger log = LoggerFactory.getLogger(CsvExportService.class);

    // ── Cabeceras CSV ─────────────────────────────────────────────────────────

    private static final String PRODUCT_HEADERS = "SKU,Nombre,Categoria,Marca,Precio,Precio_Descuento,Stock,Activo,Destacado";

    private static final String USER_HEADERS = "Nombre,Apellidos,Correo,Telefono,Rol,Activo,EsCliente,TotalOrdenes";

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CsvExportService(ProductRepository productRepository,
            UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Exporta todos los productos activos e inactivos a CSV.
     *
     * @return bytes del archivo CSV listo para descargar
     */
    public byte[] exportProducts() throws IOException {
        List<Product> products = productRepository.findAll();
        log.info("[CsvExport] Exportando {} productos", products.size());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {

            // BOM para Excel Windows
            bos.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

            writer.println(PRODUCT_HEADERS);
            for (Product p : products) {
                writer.println(String.join(",",
                        escape(p.getSku()),
                        escape(p.getName()),
                        escape(p.getCategory() != null ? p.getCategory().getName() : ""),
                        escape(p.getBrand() != null ? p.getBrand().getName() : ""),
                        p.getPrice() != null ? p.getPrice().toPlainString() : "",
                        p.getDiscountPrice() != null ? p.getDiscountPrice().toPlainString() : "",
                        p.getStock() != null ? String.valueOf(p.getStock()) : "0",
                        p.getActive() != null ? (p.getActive() ? "SI" : "NO") : "NO",
                        p.getFeatured() != null ? (p.getFeatured() ? "SI" : "NO") : "NO"));
            }
            writer.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Exporta todos los usuarios (clientes y empleados) a CSV.
     * Se excluyen contraseñas, tokens y datos sensibles de seguridad.
     *
     * @return bytes del archivo CSV listo para descargar
     */
    public byte[] exportUsers() throws IOException {
        List<User> users = userRepository.findAll();
        log.info("[CsvExport] Exportando {} usuarios", users.size());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {

            bos.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

            writer.println(USER_HEADERS);
            for (User u : users) {
                String rol = u.getRoles() != null && !u.getRoles().isEmpty()
                        ? u.getRoles().iterator().next().getName()
                        : "SIN_ROL";

                writer.println(String.join(",",
                        escape(u.getFirstName()),
                        escape(u.getLastName()),
                        escape(u.getEmail()),
                        escape(u.getPhone() != null ? u.getPhone() : ""),
                        escape(rol),
                        u.getEnabled() != null ? (u.getEnabled() ? "SI" : "NO") : "NO",
                        u.getIsCustomer() != null ? (u.getIsCustomer() ? "SI" : "NO") : "NO",
                        u.getTotalOrders() != null ? String.valueOf(u.getTotalOrders()) : "0"));
            }
            writer.flush();
            return bos.toByteArray();
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * Escapa un valor para CSV: si contiene coma, comilla doble o salto de línea
     * lo envuelve en comillas dobles y duplica las comillas internas.
     */
    private String escape(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
