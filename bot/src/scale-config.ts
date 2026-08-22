const DEFAULT_SCALE_URL = 'http://192.168.1.50';

let scaleUrl = normalizeScaleUrl(process.env.SCALE_URL) ?? DEFAULT_SCALE_URL;

function normalizeScaleUrl(value: unknown): string | null {
  const raw = String(value ?? '').trim();
  if (!raw) return null;

  const withProtocol = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;

  try {
    const url = new URL(withProtocol);
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    return url.toString().replace(/\/$/, '');
  } catch {
    return null;
  }
}

export function getScaleUrl(): string {
  return scaleUrl;
}

export function setScaleUrl(value: string): string | null {
  const normalized = normalizeScaleUrl(value);
  if (!normalized) return null;

  scaleUrl = normalized;
  return scaleUrl;
}

export function getDefaultScaleUrl(): string {
  return normalizeScaleUrl(process.env.SCALE_URL) ?? DEFAULT_SCALE_URL;
}
