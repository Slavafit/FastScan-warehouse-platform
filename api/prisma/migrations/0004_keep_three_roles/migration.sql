ALTER TYPE "Role" RENAME TO "Role_with_manager";

CREATE TYPE "Role" AS ENUM ('ADMIN', 'WORKER', 'CLIENT');

ALTER TABLE "app_user" ALTER COLUMN "role" DROP DEFAULT;
ALTER TABLE "app_user"
  ALTER COLUMN "role" TYPE "Role"
  USING ("role"::text::"Role");
ALTER TABLE "app_user" ALTER COLUMN "role" SET DEFAULT 'WORKER';

DROP TYPE "Role_with_manager";
