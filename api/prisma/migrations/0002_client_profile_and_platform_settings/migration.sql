-- Extend client cabinets with contact details.
ALTER TABLE "client_account"
ADD COLUMN "phone" VARCHAR(40),
ADD COLUMN "address" VARCHAR(500);

-- Keep warehouse-wide values in the administrator-managed settings row.
ALTER TABLE "client_code_config"
ADD COLUMN "warehouse_name" VARCHAR(120) NOT NULL DEFAULT 'BMG',
ADD COLUMN "country" VARCHAR(120) NOT NULL DEFAULT 'España';
