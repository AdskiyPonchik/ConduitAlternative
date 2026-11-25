import { isFetchError } from 'src/services'

export function formErrors(error: unknown): Record<string, string[]> {
  if (isFetchError(error) && ![401, 403, 404, 429].includes(error.status)
    && error.error?.errors && Object.keys(error.error.errors).length) return error.error.errors
  return { '': errorMessages(error) }
}

export function errorMessages(error: unknown, fallback = 'Unable to complete the request. Please try again.'): string[] {
  if (isFetchError(error)) {
    if (error.status === 401) return ['Your session has expired. Sign in again to continue.']
    if (error.status === 403) return ['You do not have permission to change this content.']
    if (error.status === 404) return ['The requested content was not found.']
    if (error.status === 429) return ['Too many requests. Please wait before trying again.']
    const messages = Object.values(error.error?.errors ?? {}).flat()
    if (messages.length) return messages
  }
  return [fallback]
}
