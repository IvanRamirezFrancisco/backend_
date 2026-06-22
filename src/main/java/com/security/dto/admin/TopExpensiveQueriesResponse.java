package com.security.dto.admin;

import java.util.List;

/**
 * Respuesta del endpoint de queries costosas.
 * Encapsula la disponibilidad de pg_stat_statements y los datos.
 *
 * @param available Si {@code false}, la extensión no está instalada
 * @param message   Mensaje informativo cuando no está disponible
 * @param data      Lista de queries costosas (vacía si no disponible)
 */
public record TopExpensiveQueriesResponse(
                boolean available,
                String message,
                List<ExpensiveQueryDto> data) {
        /** Factory para cuando la extensión no está instalada. */
        public static TopExpensiveQueriesResponse unavailable() {
                return new TopExpensiveQueriesResponse(
                                false,
                                "Extensión pg_stat_statements no instalada. "
                                                + "Ejecute CREATE EXTENSION pg_stat_statements; con privilegios de superusuario.",
                                List.of());
        }

        /** Factory para respuesta exitosa con datos. */
        public static TopExpensiveQueriesResponse of(List<ExpensiveQueryDto> queries) {
                return new TopExpensiveQueriesResponse(true, null, queries);
        }

        /**
         * Factory para cuando la extensión está activa pero no hay queries de
         * negocio
         * (todas fueron filtradas como internas del sistema).
         */
        public static TopExpensiveQueriesResponse noBusinessQueries() {
                return new TopExpensiveQueriesResponse(
                                true,
                                "No hay consultas de negocio con suficiente historial acumulado. "
                                                + "Esto es normal si el servidor fue reiniciado recientemente "
                                                + "o el sistema tiene poco tráfico. El historial se acumula "
                                                + "automáticamente con el uso normal de la aplicación.",
                                List.of());
        }
}
