export const orderStatusLabels: Record<string, string> = {
  new:          'новый',
  expected:     'ожидается',
  at_warehouse: 'принят на склад',
  packed:       'собран',
  shipped:      'в пути',
  delivered:    'доставлен',
};

export const parcelStatusLabels: Record<string, string> = {
  assembling: 'собирается',
  ready:      'готов к отправке',
  in_box:     'готово к отправке',
  shipped:    'в пути',
  delivered:  'доставлен',
};

export const boxStatusLabels: Record<string, string> = {
  collecting:    'собирается',
  ready_to_ship: 'готов к отправке',
  shipped:       'в пути',
  delivered:     'доставлен',
};

export const shipmentStatusLabels: Record<string, string> = {
  created:          'создан',
  in_transit:       'в пути',
  out_for_delivery: 'передан на доставку',
  delivered:        'доставлен',
  exception:        'требует внимания',
  cancelled:        'отменен',
};

export const chargeStatusLabels: Record<string, string> = {
  unpaid:    'не оплачено',
  partial:   'частично оплачено',
  paid:      'оплачено',
  cancelled: 'отменено',
};

export const paymentMethodLabels: Record<string, string> = {
  cash:          'наличные',
  bank_transfer: 'банковский перевод',
  card:          'карта',
  balance:       'баланс',
  other:         'другое',
};

function labelFrom(labels: Record<string, string>, status: unknown): string {
  const key = String(status ?? '');
  return labels[key] ?? (key || 'не указан');
}

export const orderStatusLabel = (status: unknown) =>
  labelFrom(orderStatusLabels, status);

export const parcelStatusLabel = (status: unknown) =>
  labelFrom(parcelStatusLabels, status);

export const boxStatusLabel = (status: unknown) =>
  labelFrom(boxStatusLabels, status);

export const shipmentStatusLabel = (status: unknown) =>
  labelFrom(shipmentStatusLabels, status);

export const chargeStatusLabel = (status: unknown) =>
  labelFrom(chargeStatusLabels, status);

export const paymentMethodLabel = (method: unknown) =>
  labelFrom(paymentMethodLabels, method);
