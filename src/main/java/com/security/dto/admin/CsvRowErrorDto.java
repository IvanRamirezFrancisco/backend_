package com.security.dto.admin;

/**
 * Representa un error ocurrido al procesar una fila concreta del CSV importado.
 *
 * @param rowNumber Número de fila del CSV (basado en 1, sin contar la cabecera)
 * @param rawValue  Valor crudo de la fila que causó el error (para
 *                  identificarla en la UI)
 * @param reason    Descripción del error (campo faltante, SKU inválido, precio
 *                  no numérico…)
 */
public record CsvRowErrorDto(
                int rowNumber,
                String rawValue,
                String reason) {
}
