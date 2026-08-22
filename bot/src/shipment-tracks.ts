function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('ru-RU', {
    day:   '2-digit',
    month: '2-digit',
    year:  '2-digit',
  });
}

export function activeTrackText(summary: any): string {
  const active = summary?.active;
  return active?.trackNumber
    ? `<code>${escapeHtml(active.trackNumber)}</code>`
    : 'пока нет';
}

export function trackHistoryText(summary: any, limit = 5): string {
  const tracks = Array.isArray(summary?.tracks) ? summary.tracks : [];
  if (!tracks.length) return 'истории треков пока нет';

  return tracks.slice(0, limit).map((track: any) => {
    const marker = track.isActive ? 'активный' : 'старый';
    const replaced = track.replacedAt ? `, заменен ${formatDate(track.replacedAt)}` : '';
    return `• <code>${escapeHtml(track.trackNumber)}</code> · ${escapeHtml(track.carrierId)} · ${marker}${replaced}`;
  }).join('\n');
}
