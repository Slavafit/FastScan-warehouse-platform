CREATE TABLE "order_composition" (
    "id" UUID NOT NULL,
    "code" VARCHAR(50) NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "sort_order" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "order_composition_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "order_composition_code_key" ON "order_composition"("code");
CREATE INDEX "order_composition_enabled_sort_order_idx" ON "order_composition"("enabled", "sort_order");

INSERT INTO "order_composition" ("id", "code", "name", "sort_order") VALUES
('10000000-0000-4000-8000-000000000001', 'CLOTHING', 'Одежда', 10),
('10000000-0000-4000-8000-000000000002', 'SHOES', 'Обувь', 20),
('10000000-0000-4000-8000-000000000003', 'COSMETICS', 'Косметика', 30),
('10000000-0000-4000-8000-000000000004', 'BAG', 'Сумка', 40),
('10000000-0000-4000-8000-000000000005', 'ACCESSORIES', 'Аксессуары', 50),
('10000000-0000-4000-8000-000000000006', 'ELECTRONICS', 'Электроника', 60),
('10000000-0000-4000-8000-000000000007', 'OTHER', 'Другое', 100);

ALTER TABLE "shipment_order" ADD COLUMN "composition_id" UUID;
ALTER TABLE "shipment_order" ADD COLUMN "product_description" VARCHAR(500);
ALTER TABLE "shipment_order" ADD COLUMN "declared_value" DECIMAL(12,2);
ALTER TABLE "shipment_order" ADD COLUMN "currency" VARCHAR(3) NOT NULL DEFAULT 'EUR';
CREATE INDEX "shipment_order_composition_id_idx" ON "shipment_order"("composition_id");
ALTER TABLE "shipment_order" ADD CONSTRAINT "shipment_order_composition_id_fkey" FOREIGN KEY ("composition_id") REFERENCES "order_composition"("id") ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "manifest_item" ADD COLUMN "client_first_name" VARCHAR(120);
ALTER TABLE "manifest_item" ADD COLUMN "client_last_name" VARCHAR(120);
ALTER TABLE "manifest_item" ADD COLUMN "client_email" VARCHAR(320);
ALTER TABLE "manifest_item" ADD COLUMN "composition_name" VARCHAR(120);
ALTER TABLE "manifest_item" ADD COLUMN "product_description" VARCHAR(500);
ALTER TABLE "manifest_item" ADD COLUMN "declared_value" DECIMAL(12,2);
ALTER TABLE "manifest_item" ADD COLUMN "currency" VARCHAR(3);
