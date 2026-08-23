-- LMS/Telegram integration was added to this repository by mistake.
-- Keep migration history immutable and remove only the integration-specific schema.
DROP TABLE IF EXISTS "telegram_login_token";
DROP TABLE IF EXISTS "telegram_link_token";
DROP TABLE IF EXISTS "password_reset_token";

ALTER TABLE "client_account"
  DROP COLUMN IF EXISTS "preferred_notification_channel",
  DROP COLUMN IF EXISTS "telegram_user_id",
  DROP COLUMN IF EXISTS "telegram_chat_id",
  DROP COLUMN IF EXISTS "whatsapp_phone";

ALTER TABLE "client_account" ALTER COLUMN "email" SET NOT NULL;
ALTER TABLE "app_user" DROP COLUMN IF EXISTS "token_version";

DROP TYPE IF EXISTS "NotificationChannel";
