ALTER TABLE "client_code_config"
ADD COLUMN "volumetric_divisor" INTEGER NOT NULL DEFAULT 6000;

ALTER TABLE "manifest"
ADD COLUMN "volume_weight" DECIMAL(12,3) NOT NULL DEFAULT 0,
ADD COLUMN "chargeable_weight" DECIMAL(12,3) NOT NULL DEFAULT 0;

ALTER TABLE "manifest_item"
ADD COLUMN "volume_weight" DECIMAL(12,3),
ADD COLUMN "chargeable_weight" DECIMAL(12,3);
