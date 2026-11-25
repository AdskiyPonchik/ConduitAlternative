import { describe, expect, it } from 'vitest'
import { render, waitFor } from '@testing-library/vue'
import fixtures from 'src/utils/test/fixtures'
import { asyncWrapper, createTestRouter, renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import ArticleDetail from './ArticleDetail.vue'

describe('# ArticleDetail', () => {
  const server = setupMockServer(
    ['/api/articles/markdown', { article: { ...fixtures.article, body: fixtures.markdown } }],
    ['/api/articles/markdown-cn', { article: { ...fixtures.article, body: fixtures.markdownCN } }],
    ['/api/articles/markdown-xss', { article: { ...fixtures.article, body: fixtures.markdownXss } }],
  )

  it('loads a different article when the same component receives another slug', async () => {
    server.use(
      ['/api/articles/first', { article: { ...fixtures.article, title: 'First article' } }],
      ['/api/articles/second', { article: { ...fixtures.article, title: 'Second article' } }],
    )
    const router = createTestRouter()
    const { getByRole, queryByRole } = render(ArticleDetail, await renderOptions({
      router, initialRoute: { name: 'article', params: { slug: 'first' } },
    }))
    await waitFor(() => expect(getByRole('heading', { name: 'First article' })).toBeVisible())
    await router.push({ name: 'article', params: { slug: 'second' } })
    await waitFor(() => expect(getByRole('heading', { name: 'Second article' })).toBeVisible())
    expect(queryByRole('heading', { name: 'First article' })).toBeNull()
  })

  it('should render markdown body correctly', async () => {
    const { container } = render(asyncWrapper(ArticleDetail), await renderOptions({
      initialRoute: { name: 'article', params: { slug: 'markdown' } },
    }))
    await server.waitForRequest('GET', '/api/articles/markdown')

    expect(container.querySelector('#article-content')).toMatchSnapshot()
  })

  it('should render markdown (zh-CN) body correctly', async () => {
    const { container } = render(asyncWrapper(ArticleDetail), await renderOptions({
      initialRoute: { name: 'article', params: { slug: 'markdown-cn' } },
    }))
    await server.waitForRequest('GET', '/api/articles/markdown-cn')

    expect(container.querySelector('#article-content')).toMatchSnapshot()
  })

  it('should filter the xss content in Markdown body', async () => {
    const { container } = render(asyncWrapper(ArticleDetail), await renderOptions({
      initialRoute: { name: 'article', params: { slug: 'markdown-xss' } },
    }))
    await server.waitForRequest('GET', '/api/articles/markdown-xss')

    expect(container.querySelector('#article-content')).toMatchSnapshot()
  })
})
