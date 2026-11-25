import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, waitFor } from '@testing-library/vue'
import { useUserStore } from 'src/store/user'
import fixtures from 'src/utils/test/fixtures'
import { createTestRouter, renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import Login from './Login.vue'
import { api } from 'src/services'

describe('# Login page', () => {
  const server = setupMockServer()

  it('blocks duplicate submits while login is pending and shows network failures', async () => {
    let fail!: (error: unknown) => void
    const login = vi.spyOn(api.users, 'login').mockImplementation(() => new Promise((_, reject) => { fail = reject }))
    try {
      const { getByRole, getByPlaceholderText, container } = render(Login, renderOptions())
      await fireEvent.update(getByPlaceholderText('Email'), 'email@example.test')
      await fireEvent.update(getByPlaceholderText('Password'), 'password')
      const form = getByRole('form', { name: 'Login form' })
      await fireEvent.submit(form)
      await fireEvent.submit(form)
      expect(login).toHaveBeenCalledTimes(1)
      expect(getByRole('button', { name: 'Sign in' })).toBeDisabled()
      fail(new TypeError('offline'))
      await waitFor(() => expect(container).toHaveTextContent('Unable to complete'))
      expect(getByRole('button', { name: 'Sign in' })).not.toBeDisabled()
    }
    finally { login.mockRestore() }
  })

  it('should call login api when fill form and click submit button', async () => {
    const router = createTestRouter()
    server.use(['POST', '/api/users/login', { user: fixtures.user }])
    const { getByRole, getByPlaceholderText } = render(Login, renderOptions({
      router,
    }))
    const store = useUserStore()

    await fireEvent.update(getByPlaceholderText('Email'), 'email@email.com')
    await fireEvent.update(getByPlaceholderText('Password'), 'password')

    await fireEvent.click(getByRole('button', { name: 'Sign in' }))

    const mockedRequest = await server.waitForRequest('POST', '/api/users/login')

    expect(router.currentRoute.value.path).toBe('/')
    expect(store.updateUser).toHaveBeenCalledWith(fixtures.user)
    expect(await mockedRequest.json()).toMatchInlineSnapshot(`
      {
        "user": {
          "email": "email@email.com",
          "password": "password",
        },
      }
    `)
  })

  it('should display error message when api returned some errors', async () => {
    server.use(['POST', '/api/users/login', 400, { errors: { password: ['is invalid'] } }])
    const { container, getByRole, getByPlaceholderText } = render(Login, renderOptions())

    await fireEvent.update(getByPlaceholderText('Email'), 'email@email.com')
    await fireEvent.update(getByPlaceholderText('Password'), 'password')

    await fireEvent.click(getByRole('button', { name: 'Sign in' }))

    await server.waitForRequest('POST', '/api/users/login')

    expect(container).toHaveTextContent('password is invalid')
  })

  it('should not trigger api call when user submit a invalid form', async () => {
    const { getByRole, getByPlaceholderText } = render(Login, renderOptions())
    const formElement = getByRole<HTMLFormElement>('form', { name: 'Login form' })
    vi.spyOn(formElement, 'checkValidity')

    expect(getByRole('button', { name: 'Sign in' })).toHaveProperty('disabled', true)

    await fireEvent.update(getByPlaceholderText('Email'), 'email')
    await fireEvent.update(getByPlaceholderText('Password'), 'password')

    await fireEvent.click(getByRole('button', { name: 'Sign in' }))

    expect(formElement.checkValidity).toHaveBeenCalled()
  })
})
