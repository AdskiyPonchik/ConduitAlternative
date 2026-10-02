import { CONFIG } from 'src/config'
import type { GenericErrorModel, HttpResponse } from 'src/services/api'
import { Api, ContentType } from 'src/services/api'
import { getSession, onSessionChange, setSession } from 'src/services/session'

export const limit = 10

export const api = new Api({
  customFetch: async (input, init) => {
    const authorization = new Headers(init?.headers).get('Authorization')
    const response = await fetch(input, init)
    const token = getSession()?.token
    if (response.status === 401 && token && authorization === `Token ${token}`) {
      setSession(null)
    }
    return response
  },
  baseUrl: `${CONFIG.API_HOST}/api`,
  securityWorker: token => token ? { headers: { Authorization: `Token ${String(token)}` } } : {},
  baseApiParams: {
    headers: {
      'content-type': ContentType.Json,
    },
    format: 'json',
    secure: true,
  },
})

api.setSecurityData(getSession()?.token ?? null)
onSessionChange(user => api.setSecurityData(user?.token ?? null))

export async function restoreSession(): Promise<void> {
  const token = getSession()?.token
  if (!token) return
  try {
    const response = await api.user.getCurrentUser()
    if (getSession()?.token === token) setSession(response.data.user)
  }
  catch (error) {
    // A 401 clears the session in customFetch. A network failure should not erase it.
    if (!isFetchError(error) || error.status !== 401) console.error(error)
  }
}

export function pageToOffset(page: number = 1, localLimit = limit): { limit: number, offset: number } {
  const offset = (page - 1) * localLimit
  return { limit: localLimit, offset }
}

export function isFetchError<E = GenericErrorModel>(e: unknown): e is HttpResponse<unknown, E> {
  return e instanceof Object && 'error' in e
}
