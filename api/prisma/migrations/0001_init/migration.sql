-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "Role" AS ENUM ('ADMIN', 'WORKER', 'CLIENT');

-- CreateEnum
CREATE TYPE "NotificationType" AS ENUM ('INFO', 'WELCOME', 'ORDER_REGISTERED', 'ORDER_PACKED', 'ORDER_UNPACKED');

-- CreateTable
CREATE TABLE "app_user" (
    "id" UUID NOT NULL,
    "login" VARCHAR(254) NOT NULL,
    "password_hash" VARCHAR(100) NOT NULL,
    "role" "Role" NOT NULL DEFAULT 'WORKER',
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "app_user_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "receiver" (
    "id" UUID NOT NULL,
    "client_code" VARCHAR(50) NOT NULL,
    "first_name" VARCHAR(120) NOT NULL,
    "last_name" VARCHAR(120) NOT NULL,
    "package_shoes" INTEGER NOT NULL DEFAULT 0,
    "bag_package" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "receiver_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "client_account" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "receiver_id" UUID NOT NULL,
    "email" VARCHAR(254) NOT NULL,
    "email_notifications" BOOLEAN NOT NULL DEFAULT true,
    "push_notifications" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "client_account_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "client_notification" (
    "id" UUID NOT NULL,
    "client_account_id" UUID NOT NULL,
    "type" "NotificationType" NOT NULL DEFAULT 'INFO',
    "title" VARCHAR(160) NOT NULL,
    "message" VARCHAR(1000) NOT NULL,
    "read_at" TIMESTAMPTZ,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "client_notification_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "partial_batch" (
    "id" UUID NOT NULL,
    "code_id" VARCHAR(50) NOT NULL,
    "partial_code" VARCHAR(100) NOT NULL,
    "partial_name" VARCHAR(160) NOT NULL,
    "warehouse_name" VARCHAR(120) NOT NULL DEFAULT 'BMG',
    "status" INTEGER NOT NULL DEFAULT 0,
    "gross_weight" DECIMAL(12,3),
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "partial_batch_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "warehouse_box" (
    "id" UUID NOT NULL,
    "code_id" VARCHAR(50) NOT NULL,
    "tracking_code" VARCHAR(160) NOT NULL,
    "warehouse" VARCHAR(120) NOT NULL DEFAULT 'BMG',
    "warehouse_guid" UUID,
    "width" INTEGER NOT NULL DEFAULT 0,
    "height" INTEGER NOT NULL DEFAULT 0,
    "length" INTEGER NOT NULL DEFAULT 0,
    "volume_weight" DECIMAL(12,3) NOT NULL DEFAULT 0,
    "gross_weight" DECIMAL(12,3),
    "status" INTEGER NOT NULL DEFAULT 0,
    "partial_id" UUID,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "warehouse_box_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "shipment_order" (
    "id" UUID NOT NULL,
    "tracking_number" VARCHAR(160) NOT NULL,
    "client_name" VARCHAR(240),
    "client_code" VARCHAR(50),
    "receiver_id" UUID,
    "with_box" INTEGER NOT NULL DEFAULT 0,
    "gross_weight" DECIMAL(12,3),
    "length" INTEGER,
    "width" INTEGER,
    "height" INTEGER,
    "status_name" VARCHAR(100) NOT NULL DEFAULT 'REGISTERED',
    "box_id" UUID,
    "box_description" VARCHAR(240),
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "shipment_order_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "client_code_config" (
    "id" SMALLINT NOT NULL DEFAULT 1,
    "code_prefix" VARCHAR(12) NOT NULL DEFAULT 'KG',
    "updated_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "client_code_config_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "operation_log" (
    "id" UUID NOT NULL,
    "user_id" UUID,
    "action" VARCHAR(20) NOT NULL,
    "route" VARCHAR(255) NOT NULL,
    "entity_type" VARCHAR(80),
    "entity_id" VARCHAR(100),
    "details" JSONB,
    "ip_address" VARCHAR(64),
    "user_agent" VARCHAR(500),
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "operation_log_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "app_user_login_key" ON "app_user"("login");

-- CreateIndex
CREATE UNIQUE INDEX "receiver_client_code_key" ON "receiver"("client_code");

-- CreateIndex
CREATE UNIQUE INDEX "client_account_user_id_key" ON "client_account"("user_id");

-- CreateIndex
CREATE UNIQUE INDEX "client_account_receiver_id_key" ON "client_account"("receiver_id");

-- CreateIndex
CREATE UNIQUE INDEX "client_account_email_key" ON "client_account"("email");

-- CreateIndex
CREATE INDEX "client_notification_client_account_id_created_at_idx" ON "client_notification"("client_account_id", "created_at");

-- CreateIndex
CREATE UNIQUE INDEX "partial_batch_code_id_key" ON "partial_batch"("code_id");

-- CreateIndex
CREATE UNIQUE INDEX "partial_batch_partial_code_key" ON "partial_batch"("partial_code");

-- CreateIndex
CREATE UNIQUE INDEX "warehouse_box_code_id_key" ON "warehouse_box"("code_id");

-- CreateIndex
CREATE UNIQUE INDEX "warehouse_box_tracking_code_key" ON "warehouse_box"("tracking_code");

-- CreateIndex
CREATE INDEX "warehouse_box_status_idx" ON "warehouse_box"("status");

-- CreateIndex
CREATE INDEX "warehouse_box_partial_id_idx" ON "warehouse_box"("partial_id");

-- CreateIndex
CREATE UNIQUE INDEX "shipment_order_tracking_number_key" ON "shipment_order"("tracking_number");

-- CreateIndex
CREATE INDEX "shipment_order_receiver_id_idx" ON "shipment_order"("receiver_id");

-- CreateIndex
CREATE INDEX "shipment_order_box_id_idx" ON "shipment_order"("box_id");

-- CreateIndex
CREATE INDEX "operation_log_created_at_idx" ON "operation_log"("created_at");

-- CreateIndex
CREATE INDEX "operation_log_user_id_idx" ON "operation_log"("user_id");

-- AddForeignKey
ALTER TABLE "client_account" ADD CONSTRAINT "client_account_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "client_account" ADD CONSTRAINT "client_account_receiver_id_fkey" FOREIGN KEY ("receiver_id") REFERENCES "receiver"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "client_notification" ADD CONSTRAINT "client_notification_client_account_id_fkey" FOREIGN KEY ("client_account_id") REFERENCES "client_account"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "warehouse_box" ADD CONSTRAINT "warehouse_box_partial_id_fkey" FOREIGN KEY ("partial_id") REFERENCES "partial_batch"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "shipment_order" ADD CONSTRAINT "shipment_order_receiver_id_fkey" FOREIGN KEY ("receiver_id") REFERENCES "receiver"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "shipment_order" ADD CONSTRAINT "shipment_order_box_id_fkey" FOREIGN KEY ("box_id") REFERENCES "warehouse_box"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "operation_log" ADD CONSTRAINT "operation_log_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE SET NULL ON UPDATE CASCADE;
