import { describe, expect, it } from 'vitest'
import userEvent from '@testing-library/user-event'
import { fireEvent, render, waitFor } from '@testing-library/vue'
import fixtures from 'src/utils/test/fixtures'
import { createTestRouter, renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import EditArticle from './EditArticle.vue'

const initialState = { user: { user: fixtures.user } }

describe('# EditArticle page', () => {
  const server = setupMockServer()

  it('creates an article with its selected tags', async () => {
    server.use(['POST', '/api/articles', { article: { ...fixtures.article, slug: 'article-title' } }])
    const router = createTestRouter()
    const { getByRole, getByPlaceholderText } = render(EditArticle, await renderOptions({
      router, initialState, initialRoute: { name: 'create-article' },
    }))

    await fireEvent.update(getByPlaceholderText('Article Title'), 'Article Title')
    await fireEvent.update(getByPlaceholderText("What's this article about?"), 'Article descriptions')
    await fireEvent.update(getByPlaceholderText('Write your article (in markdown)'), 'this is **article body**.')
    await userEvent.type(getByPlaceholderText('Enter tags'), 'tag1{Enter}tag2{Enter}')
    const request = server.waitForRequest('POST', '/api/articles')
    await fireEvent.click(getByRole('button', { name: 'Publish Article' }))

    expect(await (await request).json()).toEqual({ article: {
      title: 'Article Title', description: 'Article descriptions',
      body: 'this is **article body**.', tagList: ['tag1', 'tag2'],
    } })
    await waitFor(() => expect(router.currentRoute.value.params.slug).toBe('article-title'))
  })

  it('keeps existing tags read only and sends only editable fields when updating', async () => {
    server.use(
      ['GET', '/api/articles/*', { article: fixtures.article }],
      ['PUT', '/api/articles/*', { article: fixtures.article }],
    )
    const router = createTestRouter()
    const { container, getByRole, getByPlaceholderText, queryByPlaceholderText, queryByRole } = render(EditArticle, await renderOptions({
      router, initialState, initialRoute: { name: 'edit-article', params: { slug: 'article-foo' } },
    }))
    await server.waitForRequest('GET', '/api/articles/*')

    expect(container.querySelector('.tag-list')).toHaveTextContent('foo')
    expect(queryByPlaceholderText('Enter tags')).not.toBeInTheDocument()
    expect(queryByRole('button', { name: 'Delete tag: foo' })).not.toBeInTheDocument()
    await fireEvent.update(getByPlaceholderText('Article Title'), 'Updated title')
    const request = server.waitForRequest('PUT', '/api/articles/article-foo')
    await fireEvent.click(getByRole('button', { name: 'Publish Article' }))

    expect(await (await request).json()).toEqual({ article: {
      title: 'Updated title', description: fixtures.article.description, body: fixtures.article.body,
    } })
    await waitFor(() => expect(router.currentRoute.value.params.slug).toBe('article-foo'))
  })

  it('can remove a selected tag before creating an article', async () => {
    server.use(['POST', '/api/articles', { article: fixtures.article }])
    const { getByRole, getByPlaceholderText } = render(EditArticle, await renderOptions({
      initialState, initialRoute: { name: 'create-article' },
    }))
    await fireEvent.update(getByPlaceholderText('Article Title'), 'Article Title')
    await fireEvent.update(getByPlaceholderText("What's this article about?"), 'Description')
    await fireEvent.update(getByPlaceholderText('Write your article (in markdown)'), 'Body')
    await userEvent.type(getByPlaceholderText('Enter tags'), 'tag1{Enter}tag2{Enter}')
    await userEvent.click(getByRole('button', { name: 'Delete tag: tag1' }))
    const request = server.waitForRequest('POST', '/api/articles')
    await fireEvent.click(getByRole('button', { name: 'Publish Article' }))
    expect((await (await request).json()).article.tagList).toEqual(['tag2'])
  })

  it('shows API errors without losing article text', async () => {
    server.use(['POST', '/api/articles', 400, { errors: { body: ['Article title is too long'] } }])
    const { container, getByRole, getByPlaceholderText } = render(EditArticle, await renderOptions({
      initialState, initialRoute: { name: 'create-article' },
    }))
    await fireEvent.update(getByPlaceholderText('Article Title'), 'Article Title')
    await fireEvent.update(getByPlaceholderText("What's this article about?"), 'Description')
    await fireEvent.update(getByPlaceholderText('Write your article (in markdown)'), 'Unsaved body')
    const request = server.waitForRequest('POST', '/api/articles')
    await fireEvent.click(getByRole('button', { name: 'Publish Article' }))
    await request
    expect(container).toHaveTextContent('Article title is too long')
    expect(getByPlaceholderText('Write your article (in markdown)')).toHaveValue('Unsaved body')
    expect(getByRole('button', { name: 'Publish Article' })).not.toBeDisabled()
  })
})
