import type { User } from 'src/services/api'
import Storage from 'src/utils/storage'
import { ref } from 'vue'

export const sessionNotice = ref('')

export const userStorage = new Storage<User>('conduit-java:user')
const listeners = new Set<(user: User | null) => void>()

export function getSession(): User | null {
  const user = userStorage.get()
  return user && typeof user.token === 'string' && user.token ? user : null
}

export function setSession(user: User | null, expired = false): void {
  sessionNotice.value = expired ? 'Your session has expired. Sign in again to continue. Your saved drafts are kept in this tab.' : ''
  if (user) userStorage.set(user)
  else userStorage.remove()
  for (const listener of listeners) listener(user)
}

export function onSessionChange(listener: (user: User | null) => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}
