import { afterEach, describe, expect, it, vi } from 'vitest'
import fixtures from 'src/utils/test/fixtures'
import { getSession, onSessionChange, setSession, userStorage } from './session'

const user = { ...fixtures.user, token: 'java-token' }

afterEach(() => {
  setSession(null)
  localStorage.removeItem('user')
})

describe('Java session storage', () => {
  it('does not reuse the old backend session', () => {
    localStorage.setItem('user', JSON.stringify({ ...user, token: 'legacy-token' }))
    expect(getSession()).toBeNull()

    setSession(user)
    expect(getSession()).toEqual(user)
    expect(userStorage.get()).toEqual(user)
    expect(JSON.parse(localStorage.getItem('user')!).token).toBe('legacy-token')

    setSession(null)
    expect(userStorage.get()).toBeNull()
  })

  it('notifies subscribers on login and logout and supports unsubscription', () => {
    const listener = vi.fn()
    const unsubscribe = onSessionChange(listener)
    setSession(user)
    setSession(null)
    expect(listener.mock.calls).toEqual([[user], [null]])

    unsubscribe()
    setSession(user)
    expect(listener).toHaveBeenCalledTimes(2)
  })
})
