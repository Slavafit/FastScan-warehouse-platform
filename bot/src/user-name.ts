export interface UserNameLike {
  name?: string | null;
  lastName?: string | null;
}

export function userFullName(user: UserNameLike | null | undefined, fallback = 'Клиент'): string {
  if (!user) return fallback;
  return [user.name, user.lastName].filter(Boolean).join(' ').trim() || fallback;
}
