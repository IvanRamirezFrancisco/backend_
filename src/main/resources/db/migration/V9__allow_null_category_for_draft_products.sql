-- =============================================================================
-- V9: Permitir NULL en category_id para borradores importados por CSV
-- =============================================================================
-- Contexto: El módulo de importación CSV guarda productos como "Borrador"
-- (active = false) cuando la categoría indicada en el archivo no existe en el
-- sistema. El panel de administración exige categoría al crear/editar manualmente
-- (validado en ProductDTO, capa de controlador), por lo que esta columna puede
-- ser nullable a nivel de base de datos sin riesgo de integridad de negocio.
-- =============================================================================
ALTER TABLE products
ALTER COLUMN category_id DROP NOT NULL;