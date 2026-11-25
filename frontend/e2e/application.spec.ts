import { createHmac, randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'

const password = 'e2e-account-password-123'
const signingKey = Buffer.from('MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=', 'base64')

async function account(request: APIRequestContext) {
  const username = `e2e-${randomUUID()}`
  const response = await request.post('/api/users', { data: { user: { username, email: `${username}@example.test`, password } } })
  expect(response.status()).toBe(201)
  return (await response.json()).user
}

async function signIn(page: Page, email: string) {
  await page.goto('/login')
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/')
}

function expiredToken(token: string) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256' })).toString('base64url')
  const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
  const payload = Buffer.from(JSON.stringify({ ...claims, exp: 1, nbf: 0, iat: 0 })).toString('base64url')
  const data = `${header}.${payload}`
  return `${data}.${createHmac('sha256', signingKey).update(data).digest('base64url')}`
}

test('registration, logout and login use the real API', async ({ page }) => {
  const username = `browser-${randomUUID()}`
  await page.goto('/register')
  await page.getByLabel('Username', { exact: true }).fill(username)
  await page.getByLabel('Email', { exact: true }).fill(`${username}@example.test`)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign up', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Settings', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Settings', exact: true }).click()
  await page.getByRole('button', { name: 'Logout', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
  await signIn(page, `${username}@example.test`)
  await expect(page.getByRole('link', { name: username, exact: true })).toBeVisible()
})

test('article CRUD, comments, favorites, follows and ownership', async ({ page, request }) => {
  const author = await account(request)
  const reader = await account(request)
  await signIn(page, author.email)
  await page.getByRole('link', { name: 'New Post', exact: true }).click()
  const title = `Browser article ${randomUUID()}`
  await page.getByLabel('Title', { exact: true }).fill(title)
  await page.getByLabel('Description', { exact: true }).fill('Created against Spring Boot and PostgreSQL')
  await page.getByLabel('Body', { exact: true }).fill('**Original** content')
  await page.getByLabel('Tags', { exact: true }).fill('e2e')
  await page.getByLabel('Tags', { exact: true }).press('Enter')
  await page.getByRole('button', { name: 'Publish Article' }).click()
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  const articlePath = new URL(page.url()).pathname
  const slug = articlePath.split('/').pop()!
  await page.getByRole('link', { name: 'Edit article', exact: true }).first().click()
  await page.getByLabel('Body', { exact: true }).fill('**Updated** content')
  await page.getByRole('button', { name: 'Publish Article' }).click()
  await expect(page.getByTestId('article-body')).toHaveText('Updated content')
  await page.getByLabel('Write comment', { exact: true }).fill('A real browser comment')
  await page.getByRole('button', { name: 'Submit', exact: true }).click()
  await expect(page.getByText('A real browser comment', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Delete comment', exact: true }).click()
  await expect(page.getByText('A real browser comment', { exact: true })).toHaveCount(0)
  const protectedComment = await request.post(`/api/articles/${slug}/comments`, {
    headers: { Authorization: `Token ${author.token}` }, data: { comment: { body: 'Owner-only comment' } },
  })
  expect(protectedComment.status()).toBe(201)
  const commentId = (await protectedComment.json()).comment.id

  // Separate browser login; the author's data remains in the same database.
  await page.goto('/settings')
  await page.getByRole('button', { name: 'Logout', exact: true }).click()
  await signIn(page, reader.email)
  await page.goto(articlePath)
  await page.getByRole('button', { name: 'Favorite article', exact: true }).first().click()
  await expect(page.getByRole('button', { name: 'Unfavorite article', exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: 'Follow', exact: true }).first().click()
  await expect(page.getByRole('button', { name: 'Unfollow', exact: true }).first()).toBeVisible()
  await page.goto('/my-feeds')
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  await page.goto(`/profile/${reader.username}/favorites`)
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  const denied = await request.put(`/api/articles/${slug}`, {
    headers: { Authorization: `Token ${reader.token}` }, data: { article: { title: 'Stolen' } },
  })
  expect(denied.status()).toBe(403)
  expect((await request.delete(`/api/articles/${slug}`, { headers: { Authorization: `Token ${reader.token}` } })).status()).toBe(403)
  expect((await request.delete(`/api/articles/${slug}/comments/${commentId}`, {
    headers: { Authorization: `Token ${reader.token}` },
  })).status()).toBe(403)
  await page.goto(articlePath)
  await page.getByRole('button', { name: 'Unfavorite article', exact: true }).first().click()
  await page.getByRole('button', { name: 'Unfollow', exact: true }).first().click()
  await page.goto('/my-feeds')
  await expect(page.getByRole('heading', { name: title, exact: true })).toHaveCount(0)
  await page.goto('/settings')
  await page.getByRole('button', { name: 'Logout', exact: true }).click()
  await signIn(page, author.email)
  await page.goto(articlePath)
  await page.getByRole('button', { name: 'Delete article', exact: true }).first().click()
  await expect(page).toHaveURL('/')
  expect((await request.get(`/api/articles/${slug}`)).status()).toBe(404)
})

test('expired session returns to the editor and restores its draft', async ({ page, request }) => {
  const user = await account(request)
  const expired = { ...user, token: expiredToken(user.token) }
  await page.goto('/about')
  await page.evaluate(value => localStorage.setItem('conduit-java:user', JSON.stringify(value)), expired)
  // Delay the real session check so the editor can be used before it completes.
  let release!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/user', async route => { await held; await route.continue() })
  await page.goto('/article/create')
  await page.getByLabel('Title', { exact: true }).fill('Preserved title')
  await page.getByLabel('Description', { exact: true }).fill('Preserved description')
  await page.getByLabel('Body', { exact: true }).fill('Preserved draft text')
  await page.getByRole('button', { name: 'Publish Article' }).click()
  await expect(page).toHaveURL(/\/login\?redirect=/)
  release()
  await page.getByLabel('Email', { exact: true }).fill(user.email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/article/create')
  await expect(page.getByLabel('Body', { exact: true })).toHaveValue('Preserved draft text')
})

test('public reads recover from an expired token and nested URLs reload', async ({ page, request }) => {
  const user = await account(request)
  const articles = (await (await request.get('/api/articles?limit=2')).json()).articles
  await page.goto('/about')
  await page.evaluate(value => localStorage.setItem('conduit-java:user', JSON.stringify(value)), { ...user, token: expiredToken(user.token) })
  await page.goto(`/article/${articles[0].slug}`)
  await expect(page.getByRole('heading', { name: articles[0].title, exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: articles[0].title, exact: true })).toBeVisible()
  // Exercise Vue Router's same-component navigation through browser history.
  await page.evaluate(path => {
    history.pushState({}, '', path)
    dispatchEvent(new PopStateEvent('popstate'))
  }, `/article/${articles[1].slug}`)
  await expect(page.getByRole('heading', { name: articles[1].title, exact: true })).toBeVisible()
  await page.goto('/article/does-not-exist')
  await expect(page.getByRole('alert').first()).toContainText('not found')
  await page.goto('/about')
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Inside Conduit.' })).toBeVisible()
})

test('transport failure shows an error and retry recovers real content', async ({ page }) => {
  await page.route('**/api/articles?*', route => route.abort('failed'))
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('Unable to complete')
  await page.unroute('**/api/articles?*')
  await page.getByRole('button', { name: 'Try again', exact: true }).click()
  await expect(page.getByTestId('article-preview').first()).toBeVisible()
})

test('sample data supports pagination and tag filters', async ({ page, request }) => {
  const total = (await (await request.get('/api/articles')).json()).articlesCount
  await page.goto('/')
  await expect(page.getByTestId('article-preview')).toHaveCount(10)
  const firstPageTitle = await page.getByTestId('article-preview').first().getByRole('heading').innerText()
  await page.getByRole('link', { name: 'Go to page 2', exact: true }).click()
  await expect(page.getByTestId('article-preview')).toHaveCount(Math.min(10, total - 10))
  await expect(page.getByRole('heading', { name: firstPageTitle, exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: 'java', exact: true }).click()
  await expect(page).toHaveURL('/tag/java')
  await expect(page.getByTestId('article-preview')).toHaveCount(5)
  const previews = page.getByTestId('article-preview')
  for (let i = 0; i < await previews.count(); i++) await expect(previews.nth(i).locator('.tag-list')).toContainText('java')
})
