import { afterEach, describe, expect, it } from 'vitest'
import { readDraft, removeDraft, saveDraft } from './drafts'

afterEach(() => sessionStorage.clear())

describe('tab-local drafts', () => {
  it('keeps accounts and articles separate and removes successfully submitted drafts', () => {
    saveDraft('article:alice:new', { body: 'Alice draft' })
    saveDraft('article:bob:new', { body: 'Bob draft' })
    saveDraft('comment:alice:article-one', 'Unsent comment')
    expect(readDraft('article:alice:new')).toEqual({ body: 'Alice draft' })
    expect(readDraft('article:bob:new')).toEqual({ body: 'Bob draft' })
    expect(readDraft('comment:alice:article-two')).toBeNull()
    removeDraft('article:alice:new')
    expect(readDraft('article:alice:new')).toBeNull()
    expect(readDraft('comment:alice:article-one')).toBe('Unsent comment')
  })

  it('ignores corrupt saved JSON', () => {
    sessionStorage.setItem('conduit-java:draft:article:alice:new', '{corrupt')
    expect(readDraft('article:alice:new')).toBeNull()
  })
})
