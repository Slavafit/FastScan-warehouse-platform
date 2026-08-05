CREATE TYPE "DictionaryType" AS ENUM ('WAREHOUSE', 'COUNTRY', 'ORDER_STATUS', 'CURRENCY', 'PACKAGING_TYPE');

CREATE TABLE "dictionary_item" (
    "id" UUID NOT NULL,
    "type" "DictionaryType" NOT NULL,
    "code" VARCHAR(50) NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "sort_order" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "dictionary_item_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "dictionary_item_type_code_key" ON "dictionary_item"("type", "code");
CREATE INDEX "dictionary_item_type_enabled_sort_order_idx" ON "dictionary_item"("type", "enabled", "sort_order");

INSERT INTO "dictionary_item" ("id", "type", "code", "name", "sort_order") VALUES
('20000000-0000-4000-8000-000000000001', 'WAREHOUSE', 'BMG', 'BMG', 10),
('20000000-0000-4000-8000-000000000002', 'COUNTRY', 'ES', 'España', 10),
('20000000-0000-4000-8000-000000000003', 'ORDER_STATUS', 'REGISTERED', 'Registered', 10),
('20000000-0000-4000-8000-000000000004', 'ORDER_STATUS', 'PACKED', 'Packed', 20),
('20000000-0000-4000-8000-000000000005', 'ORDER_STATUS', 'IN_BATCH', 'In batch', 30),
('20000000-0000-4000-8000-000000000006', 'CURRENCY', 'EUR', 'Euro', 10),
('20000000-0000-4000-8000-000000000007', 'CURRENCY', 'USD', 'US Dollar', 20),
('20000000-0000-4000-8000-000000000008', 'PACKAGING_TYPE', 'BOX', 'Box', 10),
('20000000-0000-4000-8000-000000000009', 'PACKAGING_TYPE', 'BAG', 'Bag', 20);
