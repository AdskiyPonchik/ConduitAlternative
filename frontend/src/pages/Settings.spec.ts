import { afterEach, describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { fireEvent, render, waitFor } from '@testing-library/vue'
import { router } from 'src/router'
import { useUserStore } from 'src/store/user'
import fixtures from 'src/utils/test/fixtures'
import { renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import { api } from 'src/services'
import Settings from './Settings.vue'

afterEach(() => vi.restoreAllMocks())

describe('# Settings Page', () => {
  const server = setupMockServer()

  it('should render correctly', async () => {
    const { container } = render(Settings, renderOptions({
      initialState: { user: { user: fixtures.user } },
    }))

    expect(container).toHaveTextContent('Your Settings')
  })

  it('should jump to login page when user not logged', async () => {
    vi.spyOn(router, 'push')
    render(Settings, await renderOptions({
      router,
      initialState: { user: { user: null } },
      initialRoute: '/settings',
    }))

    await waitFor(() => expect(router.push).toBeCalled())
  })

  it('should jump to home page and clear logged state when click logout button', async () => {
    vi.spyOn(router, 'push')
    const { getByRole } = render(Settings, await renderOptions({
      router,
      initialState: { user: { user: fixtures.user } },
      initialRoute: '/settings',
    }))
    const store = useUserStore()

    await fireEvent.click(getByRole('button', { name: 'Logout' }))

    expect(store.isAuthorized).toBe(false)
    expect(router.push).toHaveBeenCalledWith({ name: 'global-feed' })
  })

  it('should not trigger update api when user click submit directly', async () => {
    const { getByRole } = render(Settings, await renderOptions({
      router,
      initialState: { user: { user: fixtures.user } },
      initialRoute: '/settings',
    }))

    expect(getByRole('button', { name: 'Update Settings' })).toHaveProperty('disabled')
  })

  it('should submit new settings when submit form', async () => {
    vi.spyOn(router, 'push')
    server.use(['PUT', '/api/user', { user: { ...fixtures.user, username: 'new username' } }])
    const { getByRole, getByPlaceholderText } = render(Settings, await renderOptions({
      router,
      initialState: { user: { user: fixtures.user } },
      initialRoute: '/settings',
    }))

    await fireEvent.update(getByPlaceholderText('Your name'), 'new username')
    await fireEvent.update(getByPlaceholderText(/^New password/), 'new-password-12345')
    await fireEvent.click(getByRole('button', { name: 'Update Settings' }))

    const mockedRequest = await server.waitForRequest('PUT', '/api/user')
    expect(router.push).toHaveBeenCalledWith({ name: 'profile', params: { username: 'new username' } })
    expect(await mockedRequest.json()).toMatchInlineSnapshot(`
      {
        "user": {
          "bio": "Author bio",
          "email": "foo@example.com",
          "image": "",
          "password": "new-password-12345",
          "username": "new username",
        },
      }
    `)
  })

  it('should display error message when api returned some errors', async () => {
    server.use(['PUT', '/api/user', 400, { errors: { username: ['has already been taken'] } }])
    const { getByRole, getByPlaceholderText, getByText } = render(Settings, renderOptions({
      initialState: { user: { user: fixtures.user } },
    }))

    await userEvent.type(getByPlaceholderText('Your name'), 'new username')
    await userEvent.click(getByRole('button', { name: 'Update Settings' }))

    expect(getByText('username has already been taken')).toBeInTheDocument()
  })

  it('omits an empty password while saving other settings', async () => {
    server.use(['PUT', '/api/user', { user: { ...fixtures.user, bio: 'Changed biography' } }])
    const { getByRole, getByPlaceholderText } = render(Settings, renderOptions({
      initialState: { user: { user: fixtures.user } },
    }))

    await fireEvent.update(getByPlaceholderText('Short bio about you'), 'Changed biography')
    await fireEvent.click(getByRole('button', { name: 'Update Settings' }))

    const request = await server.waitForRequest('PUT', '/api/user')
    const { user } = await request.json()
    expect(user.bio).toBe('Changed biography')
    expect(user).not.toHaveProperty('password')
  })

  it('rejects a nonempty new password below 15 Unicode code points', async () => {
    const updateUser = vi.spyOn(api.user, 'updateCurrentUser')
    const { container, getByRole, getByPlaceholderText } = render(Settings, renderOptions({
      initialState: { user: { user: fixtures.user } },
    }))

    await fireEvent.update(getByPlaceholderText(/^New password/), '😀'.repeat(14))
    await fireEvent.click(getByRole('button', { name: 'Update Settings' }))

    expect(updateUser).not.toHaveBeenCalled()
    expect(container).toHaveTextContent(/password.*15/i)
  })
})
