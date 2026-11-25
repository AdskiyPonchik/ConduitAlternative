import { afterEach, describe, expect, it } from 'vitest'
import { fireEvent, render } from '@testing-library/vue'
import ArticleDetailCommentsForm from 'src/components/ArticleDetailCommentsForm.vue'
import fixtures from 'src/utils/test/fixtures'
import { renderOptions, setupMockServer } from 'src/utils/test/test.utils'
import { saveDraft } from 'src/utils/drafts'

afterEach(() => sessionStorage.clear())

describe('# ArticleDetailCommentsForm', () => {
  const server = setupMockServer(
    ['POST', '/api/articles/*/comments', { comment: { body: 'some texts...' } }],
  )

  it('restores an unsent comment for the same account and article', () => {
    saveDraft(`comment:${fixtures.user.username}:${fixtures.article.slug}`, 'Unsent comment')
    const { getByRole } = render(ArticleDetailCommentsForm, renderOptions({
      initialState: { user: { user: fixtures.user } }, props: { articleSlug: fixtures.article.slug },
    }))
    expect(getByRole('textbox', { name: 'Write comment' })).toHaveValue('Unsent comment')
  })

  it('should display sign in button when user not logged', () => {
    const { container } = render(ArticleDetailCommentsForm, renderOptions({
      initialState: { user: { user: null } },
      props: { articleSlug: fixtures.article.slug },
    }))

    expect(container).toHaveTextContent('add comments on this article')
  })

  it('should display form when user logged', async () => {
    const { getByRole } = render(ArticleDetailCommentsForm, renderOptions({
      initialState: { user: { user: fixtures.user } },
      props: { articleSlug: fixtures.article.slug },
    }))

    await fireEvent.update(getByRole('textbox', { name: 'Write comment' }), 'some texts...')
    await fireEvent.click(getByRole('button', { name: 'Submit' }))

    await server.waitForRequest('POST', '/api/articles/*/comments')
  })
})
