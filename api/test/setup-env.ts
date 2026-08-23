process.env.DATABASE_URL ??=
  'postgresql://fastscan:change-me@localhost:5432/fastscan?schema=public';
process.env.JWT_SECRET ??= 'fastscan-e2e-secret-at-least-32-characters';
process.env.BOOTSTRAP_ADMIN_LOGIN ??= 'e2e-admin';
process.env.BOOTSTRAP_ADMIN_PASSWORD ??= 'change-me';
process.env.JWT_ACCESS_MINUTES ??= '30';
process.env.JWT_REFRESH_DAYS ??= '30';
