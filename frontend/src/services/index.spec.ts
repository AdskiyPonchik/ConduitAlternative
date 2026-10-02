import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { useUserStore } from 'src/store/user'
import fixtures from 'src/utils/test/fixtures'
import { api, restoreSession } from './index'
import { getSession, setSession } from './session'

const user = { ...fixtures.user, token: 'old-token' }
let pinia: ReturnType<typeof createPinia>

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  setSession(null)
})

afterEach(() => {
  disposePinia(pinia)
  setSession(null)
  vi.restoreAllMocks()
})

function unauthorized() {
  return new Response(JSON.stringify({ errors: { body: ['Unauthorized'] } }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('API session handling', () => {
  it('clears storage and the visible user after an authenticated 401', async () => {
    setSession(user)
    const store = useUserStore()
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(unauthorized())

    await expect(api.user.getCurrentUser()).rejects.toMatchObject({ status: 401 })

    expect(new Headers(fetch.mock.calls[0][1]?.headers).get('Authorization')).toBe('Token old-token')
    expect(getSession()).toBeNull()
    expect(store.user).toBeNull()
    expect(store.isAuthorized).toBe(false)
  })

  it('does not log out a newer session when an older request returns 401', async () => {
    setSession(user)
    let respond!: (response: Response) => void
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(resolve => { respond = resolve }))
    const request = api.user.getCurrentUser()
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce())
    const newUser = { ...user, token: 'new-token' }
    setSession(newUser)
    respond(unauthorized())

    await expect(request).rejects.toMatchObject({ status: 401 })
    expect(getSession()).toEqual(newUser)
  })

  it('does not clear a saved session for a request sent without authentication', async () => {
    setSession(user)
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(unauthorized())
    await expect(api.user.getCurrentUser({ secure: false })).rejects.toMatchObject({ status: 401 })
    expect(new Headers(fetch.mock.calls[0][1]?.headers).has('Authorization')).toBe(false)
    expect(getSession()).toEqual(user)
  })

  it('keeps the session when the server reports a temporary failure', async () => {
    setSession(user)
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 503 }))
    await expect(api.user.getCurrentUser()).rejects.toMatchObject({ status: 503 })
    expect(getSession()).toEqual(user)
  })

  it('restores current server user data before navigation starts', async () => {
    setSession(user)
    const current = { ...user, username: 'renamed-on-server', bio: 'Updated elsewhere' }
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ user: current })))
    await restoreSession()
    expect(fetch.mock.calls[0][0]).toMatch(/\/api\/user$/)
    expect(getSession()).toEqual(current)
  })

  it('continues startup after an expired stored token', async () => {
    setSession(user)
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(unauthorized())
    await expect(restoreSession()).resolves.not.toThrow()
    expect(getSession()).toBeNull()
  })

  it('does not request the current user when no session is stored', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
    await restoreSession()
    expect(fetch).not.toHaveBeenCalled()
  })
})
