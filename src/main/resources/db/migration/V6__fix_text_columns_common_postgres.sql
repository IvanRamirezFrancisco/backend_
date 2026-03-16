-- Fix common text columns that may have been migrated as bytea
-- Applies to tables that are searched with LOWER() and other text usage
-- Users
ALTER TABLE users
ALTER COLUMN first_name TYPE VARCHAR(50) USING (first_name::text),
    ALTER COLUMN last_name TYPE VARCHAR(50) USING (last_name::text),
    ALTER COLUMN username TYPE VARCHAR(30) USING (username::text),
    ALTER COLUMN email TYPE VARCHAR(100) USING (email::text),
    ALTER COLUMN password TYPE VARCHAR(255) USING (password::text),
    ALTER COLUMN phone TYPE VARCHAR(20) USING (phone::text),
    ALTER COLUMN two_factor_secret TYPE VARCHAR(32) USING (two_factor_secret::text),
    ALTER COLUMN google_auth_secret TYPE VARCHAR(255) USING (google_auth_secret::text);
-- Permissions
ALTER TABLE permissions
ALTER COLUMN name TYPE VARCHAR(100) USING (name::text),
    ALTER COLUMN description TYPE VARCHAR(255) USING (description::text),
    ALTER COLUMN category TYPE VARCHAR(50) USING (category::text);
-- Brands
ALTER TABLE brands
ALTER COLUMN name TYPE VARCHAR(100) USING (name::text),
    ALTER COLUMN description TYPE TEXT USING (description::text),
    ALTER COLUMN logo_url TYPE VARCHAR(255) USING (logo_url::text),
    ALTER COLUMN website_url TYPE VARCHAR(255) USING (website_url::text),
    ALTER COLUMN country_origin TYPE VARCHAR(100) USING (country_origin::text);
-- Categories
ALTER TABLE categories
ALTER COLUMN name TYPE VARCHAR(100) USING (name::text),
    ALTER COLUMN description TYPE TEXT USING (description::text),
    ALTER COLUMN image_url TYPE VARCHAR(255) USING (image_url::text);
-- Orders
ALTER TABLE orders
ALTER COLUMN order_number TYPE VARCHAR(50) USING (order_number::text),
    ALTER COLUMN transaction_id TYPE VARCHAR(100) USING (transaction_id::text),
    ALTER COLUMN tracking_number TYPE VARCHAR(100) USING (tracking_number::text),
    ALTER COLUMN shipping_address TYPE TEXT USING (shipping_address::text),
    ALTER COLUMN billing_address TYPE TEXT USING (billing_address::text),
    ALTER COLUMN notes TYPE TEXT USING (notes::text),
    ALTER COLUMN customer_notes TYPE TEXT USING (customer_notes::text),
    ALTER COLUMN cancellation_reason TYPE TEXT USING (cancellation_reason::text);
-- Coupons
ALTER TABLE coupons
ALTER COLUMN code TYPE VARCHAR(50) USING (code::text),
    ALTER COLUMN description TYPE VARCHAR(200) USING (description::text),
    ALTER COLUMN applicable_categories TYPE TEXT USING (applicable_categories::text),
    ALTER COLUMN applicable_products TYPE TEXT USING (applicable_products::text);