// Drafts stay in this tab and are scoped to the account and article.
// Storage can be unavailable (private mode or quota); submitting must still work.
export function readDraft<T>(key: string): T | null {
  try { return JSON.parse(sessionStorage.getItem(`conduit-java:draft:${key}`) ?? 'null') as T | null }
  catch { return null }
}

export function saveDraft(key: string, value: unknown) {
  try { sessionStorage.setItem(`conduit-java:draft:${key}`, JSON.stringify(value)) }
  catch { /* Keep the in-memory form usable when storage is unavailable. */ }
}

export function removeDraft(key: string) {
  try { sessionStorage.removeItem(`conduit-java:draft:${key}`) }
  catch { /* Storage is optional. */ }
}
