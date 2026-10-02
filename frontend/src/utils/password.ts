export function validatePassword(password: string): string | undefined {
  const length = Array.from(password).length
  if (length < 15 || length > 128)
    return 'Password must contain between 15 and 128 characters.'
  if (!password.trim()) return 'Password must not be blank.'
}
