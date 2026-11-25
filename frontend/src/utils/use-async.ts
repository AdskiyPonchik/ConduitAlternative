import { ref } from 'vue'
import { router } from 'src/router'
import { isFetchError } from 'src/services'
import { getSession } from 'src/services/session'
import { errorMessages } from './errors'

export async function signInAfterExpiry(error: unknown) {
  if (isFetchError(error) && error.status === 401 && !getSession()) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

// UI actions expose failures as state rather than unhandled event promises.
export default function useAsync<T extends (...args: any[]) => unknown>(fn: T) {
  const active = ref(false)
  const error = ref('')
  async function run(...args: Parameters<T>): Promise<Awaited<ReturnType<T>> | undefined> {
    if (active.value) return
    active.value = true
    error.value = ''
    try { return await fn(...args) as Awaited<ReturnType<T>> }
    catch (cause) {
      error.value = errorMessages(cause).join(' ')
      await signInAfterExpiry(cause)
    }
    finally { active.value = false }
  }
  return { active, error, run }
}
