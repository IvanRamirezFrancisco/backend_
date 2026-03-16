-- Aumentar el tamaño de la columna image_url para soportar base64 (PostgreSQL)
-- PostgreSQL usa `ALTER COLUMN ... TYPE ...` (no `MODIFY COLUMN`)
ALTER TABLE products
ALTER COLUMN image_url TYPE TEXT USING (image_url::text);
ALTER TABLE product_images
ALTER COLUMN image_url TYPE TEXT USING (image_url::text);