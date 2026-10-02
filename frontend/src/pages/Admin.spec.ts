import { describe, expect, it } from 'vitest'
import { fireEvent, render } from '@testing-library/vue'
import { createTestRouter, renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import Admin from './Admin.vue'

describe('# Admin Page', () => {
  const server = setupMockServer()

  it('should render correct titles and fields', async () => {
    const { container } = render(Admin, renderOptions({
      initialState: { 
        user: { 
          user: { username: 'adminUser', email: 'admin@example.com', token: 'valid-token', bio: '', image: '', role: 'Admin' } 
        } 
      },
    }))
    expect(container).toHaveTextContent('Admin Dashboard')
    expect(container).toHaveTextContent('Manage User Roles')
  })

  it('should call update role api with correct payload format when form is submitted', async () => {
    const router = createTestRouter()
    server.use(['PUT', '/api/user/testuser/role', { message: 'Success' }])

    const { getByRole, getByPlaceholderText } = render(Admin, renderOptions({
      router,
      initialState: { 
        user: { 
          user: { username: 'adminUser', email: 'admin@example.com', token: 'valid-token', bio: '', image: '', role: 'Admin' } 
        } 
      },
    }))

    await fireEvent.update(getByPlaceholderText('Enter username'), 'testuser')
    await fireEvent.change(getByRole('combobox'), { target: { value: 'Moderator' } })
    await fireEvent.click(getByRole('button', { name: 'Update Role' }))

    const mockedRequest = await server.waitForRequest('PUT', '/api/user/testuser/role')
    
    expect(await mockedRequest.json()).toEqual({
      user: {
        role: 'Moderator'
      }
    })
  })
})