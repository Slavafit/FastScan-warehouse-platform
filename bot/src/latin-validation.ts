export function isEnglishName(value: string): boolean {
  return /^[A-Za-z]+(?:[ '-][A-Za-z]+)*$/.test(value.trim());
}

export function isEnglishAddress(value: string): boolean {
  return /^[A-Za-z0-9 .,'#\/()_-]+$/.test(value.trim());
}

export const ENGLISH_NAME_HINT =
  'Используйте только английские буквы. Допустимы пробел, дефис и апостроф.';

export const ENGLISH_ADDRESS_HINT =
  'Используйте английские буквы, цифры и обычные адресные символы.';
