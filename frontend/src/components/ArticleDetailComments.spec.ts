import { describe, expect, it } from 'vitest'
import { fireEvent, render, waitFor } from '@testing-library/vue'
import fixtures from 'src/utils/test/fixtures'
import { asyncWrapper, createTestRouter, renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import ArticleDetailComments from './ArticleDetailComments.vue'

describe('# ArticleDetailComments', () => {
  // const mockDeleteComment = deleteComment as jest.MockedFunction<typeof deleteComment>

  const server = setupMockServer(
    ['GET', '/api/profiles/*', { profile: fixtures.author }],
    ['GET', '/api/articles/*/comments', { comments: [fixtures.comment] }],
    ['POST', '/api/articles/*/comments', { comment: fixtures.comment2 }],
    ['DELETE', '/api/articles/*/comments/*'],
  )

  it('replaces comments when navigating to a different article in the same component', async () => {
    server.use(
      ['GET', '/api/articles/first/comments', { comments: [{ ...fixtures.comment, body: 'First comments' }] }],
      ['GET', '/api/articles/second/comments', { comments: [{ ...fixtures.comment, body: 'Second comments' }] }],
    )
    const router = createTestRouter()
    const { getByText, queryByText } = render(ArticleDetailComments, await renderOptions({
      router, initialRoute: { name: 'article', params: { slug: 'first' } },
    }))
    await waitFor(() => expect(getByText('First comments')).toBeVisible())
    await router.push({ name: 'article', params: { slug: 'second' } })
    await waitFor(() => expect(getByText('Second comments')).toBeVisible())
    expect(queryByText('First comments')).toBeNull()
  })

  it('should render correctly', async () => {
    const { container } = render(asyncWrapper(ArticleDetailComments), await renderOptions({
      initialRoute: { name: 'article', params: { slug: fixtures.article.slug } },
      initialState: { user: { user: null } },
    }))

    await server.waitForRequest('GET', '/api/articles/article-foo/comments')

    expect(container).toHaveTextContent('Comment body')
  })

  it('should display new comment when post new comment', async () => {
    const { container, getByRole } = render(asyncWrapper(ArticleDetailComments), await renderOptions({
      initialRoute: { name: 'article', params: { slug: fixtures.article.slug } },
      initialState: { user: { user: fixtures.user } },
    }))
    await server.waitForRequest('GET', '/api/articles/*/comments')
    expect(container).toHaveTextContent('Comment body')

    await fireEvent.update(getByRole('textbox', { name: 'Write comment' }), fixtures.comment2.body)
    await fireEvent.click(getByRole('button', { name: 'Submit' }))

    await server.waitForRequest('POST', '/api/articles/*/comments')
    expect(container).toHaveTextContent(fixtures.comment2.body)
  })

  it('should call remove comment service when click delete button', async () => {
    const { container, getByRole } = render(asyncWrapper(ArticleDetailComments), await renderOptions({
      initialRoute: { name: 'article', params: { slug: fixtures.article.slug } },
      initialState: { user: { user: fixtures.user } },
    }))
    await server.waitForRequest('GET', '/api/articles/article-foo/comments')

    await fireEvent.click(getByRole('button', { name: 'Delete comment' }))

    await server.waitForRequest('DELETE', '/api/articles/article-foo/comments/*')
    expect(container).not.toHaveTextContent(fixtures.comment.body)
  })
})
