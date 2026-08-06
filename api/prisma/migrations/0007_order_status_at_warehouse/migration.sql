ALTER TABLE "shipment_order" ALTER COLUMN "status_name" SET DEFAULT 'AT_WAREHOUSE';

INSERT INTO "dictionary_item" ("id", "type", "code", "name", "sort_order") VALUES
('20000000-0000-4000-8000-000000000020', 'ORDER_STATUS', 'CLIENT_REGISTERED', 'Client registered', 5),
('20000000-0000-4000-8000-000000000021', 'ORDER_STATUS', 'AT_WAREHOUSE', 'At warehouse', 10)
ON CONFLICT ("type", "code") DO NOTHING;
