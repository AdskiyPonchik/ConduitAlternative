export type FavoriteScenario = 'first' | 'repeat' | 'anonymous'

export interface RequestStep {
  title: string
  explanation: string
  code: string
  source: string
}

export const repositoryUrl = 'https://github.com/AdskiyPonchik/ConduitAlternative'
export const sourceRef = import.meta.env.VITE_SOURCE_REF || '347fd59a5091f408c97dd62f338fcbb152039c2c'
export const sourceUrl = (path: string) => `${repositoryUrl}/blob/${sourceRef}/${path}`
const java = 'src/main/java/de/conduit/'

export const scenarios: { id: FavoriteScenario, label: string, outcome: string }[] = [
  { id: 'first', label: 'First favorite', outcome: '200 OK · one favorite saved' },
  { id: 'repeat', label: 'Repeat request', outcome: '200 OK · still one favorite' },
  { id: 'anonymous', label: 'Without a token', outcome: '401 Unauthorized · no database change' },
]

export function favoriteSteps(scenario: FavoriteScenario): RequestStep[] {
  const anonymous = scenario === 'anonymous'
  const repeat = scenario === 'repeat'
  const request: RequestStep = {
    title: 'Browser sends a request',
    explanation: 'The article is identified by its slug in the URL. This request has no JSON body. The example starts with no other favorites on this article.',
    code: `POST /api/articles/example-article/favorite${anonymous ? '' : '\nAuthorization: Token <JWT>'}`,
    source: 'frontend/src/services/index.ts',
  }
  const security: RequestStep = {
    title: 'Security checks authentication',
    explanation: anonymous
      ? 'This route requires authentication. Without a token, Spring Security returns 401 before the controller is called.'
      : 'Spring Security verifies the JWT signature, timestamps, issuer, audience and UUID subject. The verified subject identifies the user.',
    code: anonymous ? 'HTTP/1.1 401 Unauthorized\n\nController: not called\nService: not called\nDatabase: unchanged' : 'Authorization header → verified Jwt\nsub → user UUID',
    source: `${java}security/${anonymous ? 'SecurityConfiguration' : 'JwtConfiguration'}.java`,
  }
  if (anonymous) return [request, security]

  return [request, security, {
    title: 'Controller passes the input',
    explanation: 'The controller extracts the user ID from the verified token and passes it, together with the article slug, to the service.',
    code: 'UUID actorId = UUID.fromString(jwt.getSubject());\narticles.favorite(actorId, slug);',
    source: `${java}articles/ArticleController.java`,
  }, {
    title: 'Service starts a transaction',
    explanation: 'The service checks that the user exists and loads the article with a write lock. Concurrent favorite operations on the same article wait for this transaction to finish.',
    code: '@Transactional\npublic ArticleView favorite(UUID actorID, String slug)\n\narticles.findBySlugForUpdate(slug)',
    source: `${java}articles/DefaultArticleService.java`,
  }, {
    title: repeat ? 'Database keeps the existing favorite' : 'Database saves the favorite',
    explanation: repeat
      ? 'The user–article pair already exists. Its composite primary key triggers ON CONFLICT DO NOTHING. No second row is inserted.'
      : 'The user–article pair is inserted. A composite primary key makes each pair unique, including when requests arrive concurrently.',
    code: 'insert into article_favorites (article_id, user_id)\nvalues (:articleId, :userId)\non conflict (article_id, user_id) do nothing',
    source: `${java}articles/ArticleFavorites.java`,
  }, {
    title: 'Response updates the interface',
    explanation: 'The service builds the response DTO. Its transaction commits before the controller sends the response. The browser updates the button and count. Only the relevant JSON fields are shown here.',
    code: 'HTTP/1.1 200 OK\n\n{\n  "article": {\n    "favorited": true,\n    "favoritesCount": 1\n  }\n}',
    source: `${java}articles/DefaultArticleService.java`,
  }]
}
