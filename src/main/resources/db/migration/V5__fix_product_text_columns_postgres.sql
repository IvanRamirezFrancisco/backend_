-- Fix incorrect bytea types after MySQL -> PostgreSQL migration
-- Ensure product text fields are stored as VARCHAR/TEXT so LOWER() works
ALTER TABLE products
ALTER COLUMN name TYPE VARCHAR(200) USING (name::text),
    ALTER COLUMN sku TYPE VARCHAR(50) USING (sku::text),
    ALTER COLUMN description TYPE TEXT USING (description::text),
    ALTER COLUMN detailed_description TYPE TEXT USING (detailed_description::text),
    ALTER COLUMN model TYPE VARCHAR(100) USING (model::text),
    ALTER COLUMN dimensions TYPE VARCHAR(100) USING (dimensions::text),
    ALTER COLUMN image_url TYPE VARCHAR(500) USING (image_url::text),
    ALTER COLUMN secondary_images TYPE TEXT USING (secondary_images::text);