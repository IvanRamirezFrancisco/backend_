package com.security.controller.admin;

import com.security.dto.admin.CsvImportResultDto;
import com.security.enums.CollisionRule;
import com.security.service.CsvExportService;
import com.security.service.CsvImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controlador REST para importación y exportación de datos en formato CSV.
 *
 * <p>
 * Base URL: {@code /api/admin/csv}
 * </p>
 *
 * <table>
 * <tr>
 * <th>Método</th>
 * <th>Ruta</th>
 * <th>Descripción</th>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/export/products</td>
 * <td>Descarga CSV de productos</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/export/users</td>
 * <td>Descarga CSV de usuarios</td>
 * </tr>
 * <tr>
 * <td>POST</td>
 * <td>/import/products</td>
 * <td>Importa productos desde CSV</td>
 * </tr>
 * <tr>
 * <td>POST</td>
 * <td>/import/users</td>
 * <td>Importa usuarios desde CSV</td>
 * </tr>
 * </table>
 */
@RestController
@RequestMapping("/api/admin/csv")
@PreAuthorize("hasRole('ADMIN')")
public class CsvController {

    private static final Logger log = LoggerFactory.getLogger(CsvController.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024; // 5 MB — defensa DoS

    private final CsvExportService csvExportService;
    private final CsvImportService csvImportService;

    public CsvController(CsvExportService csvExportService,
            CsvImportService csvImportService) {
        this.csvExportService = csvExportService;
        this.csvImportService = csvImportService;
    }

    // ── Exportaciones ─────────────────────────────────────────────────────────

    /**
     * Exporta todos los productos a un archivo CSV descargable.
     *
     * @return archivo CSV con cabecera y una fila por producto
     */
    @GetMapping("/export/products")
    public ResponseEntity<byte[]> exportProducts() throws IOException {
        log.info("[CsvController] Exportando productos a CSV");
        byte[] csv = csvExportService.exportProducts();
        return downloadResponse(csv, "productos_" + today() + ".csv");
    }

    /**
     * Exporta todos los usuarios (clientes + staff) a un archivo CSV descargable.
     *
     * @return archivo CSV con cabecera y una fila por usuario
     */
    @GetMapping("/export/users")
    public ResponseEntity<byte[]> exportUsers() throws IOException {
        log.info("[CsvController] Exportando usuarios a CSV");
        byte[] csv = csvExportService.exportUsers();
        return downloadResponse(csv, "usuarios_" + today() + ".csv");
    }

    // ── Importaciones ─────────────────────────────────────────────────────────

    /**
     * Importa productos desde un archivo CSV con regla de colisión configurable.
     *
     * <p>
     * Defensas de seguridad aplicadas aquí (antes de llegar al servicio):
     * <ul>
     * <li><b>DoS</b>: rechaza archivos mayores a 5 MB.</li>
     * <li><b>Extensión</b>: solo acepta {@code .csv}.</li>
     * </ul>
     *
     * @param file archivo CSV (multipart/form-data, campo {@code file})
     * @param rule {@code UPDATE} (default) o {@code SKIP}
     */
    @PostMapping(value = "/import/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResultDto> importProducts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "rule", defaultValue = "UPDATE") CollisionRule rule) throws IOException {

        validateCsvFile(file);
        log.info("[CsvController] Importando productos desde CSV: {} | rule={}",
                file.getOriginalFilename(), rule);
        CsvImportResultDto result = csvImportService.importProducts(file, rule);
        return ResponseEntity.ok(result);
    }

    /**
     * Importa usuarios desde un archivo CSV.
     *
     * <p>
     * El archivo debe enviarse como {@code multipart/form-data} con
     * el campo {@code file}.
     * </p>
     *
     * @param file archivo CSV con columnas:
     *             {@code Nombre,Apellidos,Correo,Telefono,Rol,Activo,EsCliente}
     * @return resumen de la operación: filas procesadas, insertadas, actualizadas y
     *         errores
     */
    @PostMapping(value = "/import/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResultDto> importUsers(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateCsvFile(file);
        log.info("[CsvController] Importando usuarios desde CSV: {}", file.getOriginalFilename());
        CsvImportResultDto result = csvImportService.importUsers(file);
        return ResponseEntity.ok(result);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> downloadResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(content);
    }

    private void validateCsvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV no puede estar vacio");
        }
        // Defensa DoS: máximo 5 MB
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new IllegalArgumentException(
                    "El archivo excede el limite de 5 MB (" + (file.getSize() / 1024 / 1024) + " MB recibidos)");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("El archivo debe tener extension .csv");
        }
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }
}
