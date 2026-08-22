CREATE TYPE "NotificationChannel" AS ENUM ('INTERNAL', 'EMAIL', 'TELEGRAM', 'WHATSAPP');

ALTER TABLE "app_user"
ADD COLUMN "token_version" INTEGER NOT NULL DEFAULT 0;

ALTER TABLE "client_account"
ADD COLUMN "preferred_notification_channel" "NotificationChannel" NOT NULL DEFAULT 'INTERNAL',
ADD COLUMN "telegram_user_id" BIGINT,
ADD COLUMN "telegram_chat_id" BIGINT,
ADD COLUMN "whatsapp_phone" VARCHAR(40);

CREATE UNIQUE INDEX "client_account_telegram_user_id_key"
ON "client_account"("telegram_user_id");

CREATE TABLE "telegram_link_token" (
  "id" UUID NOT NULL,
  "client_account_id" UUID NOT NULL,
  "code_hash" VARCHAR(64) NOT NULL,
  "expires_at" TIMESTAMPTZ NOT NULL,
  "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "telegram_link_token_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "telegram_link_token_client_account_id_fkey"
    FOREIGN KEY ("client_account_id") REFERENCES "client_account"("id") ON DELETE CASCADE
);

CREATE UNIQUE INDEX "telegram_link_token_client_account_id_key"
ON "telegram_link_token"("client_account_id");
CREATE UNIQUE INDEX "telegram_link_token_code_hash_key"
ON "telegram_link_token"("code_hash");
CREATE INDEX "telegram_link_token_expires_at_idx"
ON "telegram_link_token"("expires_at");

CREATE TABLE "password_reset_token" (
  "id" UUID NOT NULL,
  "user_id" UUID NOT NULL,
  "token_hash" VARCHAR(64) NOT NULL,
  "expires_at" TIMESTAMPTZ NOT NULL,
  "used_at" TIMESTAMPTZ,
  "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "password_reset_token_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "password_reset_token_user_id_fkey"
    FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE
);

CREATE UNIQUE INDEX "password_reset_token_token_hash_key"
ON "password_reset_token"("token_hash");
CREATE INDEX "password_reset_token_user_id_expires_at_idx"
ON "password_reset_token"("user_id", "expires_at");
