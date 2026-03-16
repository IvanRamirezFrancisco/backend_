package com.security.dto.admin;

import java.util.List;

/**
 * Resultado devuelto por el backend tras procesar un archivo CSV importado.
 *
 * @param totalRows     Total de filas de datos leídas en el archivo (sin
 *                      cabecera)
 * @param successCount  Filas procesadas con éxito (INSERT o UPDATE)
 * @param insertedCount Registros nuevos creados (INSERT)
 * @param updatedCount  Registros existentes actualizados (UPDATE/Upsert)
 * @param errorCount    Filas que fallaron y fueron omitidas
 * @param errors        Detalle de cada fila con error para mostrarla en la UI
 */
public record CsvImportResultDto(
                int totalRows,
                int successCount,
                int insertedCount,
                int updatedCount,
                int errorCount,
                List<CsvRowErrorDto> errors) {
}
