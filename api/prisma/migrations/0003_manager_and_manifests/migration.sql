-- Add the manager role without changing existing users.
ALTER TYPE "Role" ADD VALUE IF NOT EXISTS 'MANAGER' AFTER 'ADMIN';

CREATE TABLE "manifest" (
    "id" UUID NOT NULL,
    "manifest_code" VARCHAR(120) NOT NULL,
    "partial_batch_id" UUID NOT NULL,
    "created_by_user_id" UUID NOT NULL,
    "warehouse_name" VARCHAR(120) NOT NULL,
    "country" VARCHAR(120) NOT NULL,
    "batch_code" VARCHAR(100) NOT NULL,
    "batch_name" VARCHAR(160) NOT NULL,
    "box_count" INTEGER NOT NULL,
    "order_count" INTEGER NOT NULL,
    "gross_weight" DECIMAL(12,3) NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "manifest_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "manifest_item" (
    "id" UUID NOT NULL,
    "manifest_id" UUID NOT NULL,
    "source_order_id" UUID NOT NULL,
    "tracking_number" VARCHAR(160) NOT NULL,
    "client_code" VARCHAR(50),
    "client_name" VARCHAR(240),
    "client_phone" VARCHAR(40),
    "client_address" VARCHAR(500),
    "gross_weight" DECIMAL(12,3),
    "box_tracking_code" VARCHAR(160) NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "manifest_item_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "manifest_manifest_code_key" ON "manifest"("manifest_code");
CREATE UNIQUE INDEX "manifest_partial_batch_id_key" ON "manifest"("partial_batch_id");
CREATE INDEX "manifest_created_at_idx" ON "manifest"("created_at");
CREATE UNIQUE INDEX "manifest_item_manifest_id_source_order_id_key" ON "manifest_item"("manifest_id", "source_order_id");
CREATE INDEX "manifest_item_client_code_idx" ON "manifest_item"("client_code");

ALTER TABLE "manifest" ADD CONSTRAINT "manifest_partial_batch_id_fkey" FOREIGN KEY ("partial_batch_id") REFERENCES "partial_batch"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "manifest" ADD CONSTRAINT "manifest_created_by_user_id_fkey" FOREIGN KEY ("created_by_user_id") REFERENCES "app_user"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "manifest_item" ADD CONSTRAINT "manifest_item_manifest_id_fkey" FOREIGN KEY ("manifest_id") REFERENCES "manifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;
