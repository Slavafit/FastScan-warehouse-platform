ALTER TABLE "client_account"
ALTER COLUMN "email" DROP NOT NULL;

CREATE TABLE "telegram_login_token" (
  "id" UUID NOT NULL,
  "client_account_id" UUID NOT NULL,
  "token_hash" VARCHAR(64) NOT NULL,
  "expires_at" TIMESTAMPTZ NOT NULL,
  "used_at" TIMESTAMPTZ,
  "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "telegram_login_token_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "telegram_login_token_client_account_id_fkey"
    FOREIGN KEY ("client_account_id") REFERENCES "client_account"("id") ON DELETE CASCADE
);

CREATE UNIQUE INDEX "telegram_login_token_token_hash_key"
ON "telegram_login_token"("token_hash");
CREATE INDEX "telegram_login_token_client_account_id_expires_at_idx"
ON "telegram_login_token"("client_account_id", "expires_at");
