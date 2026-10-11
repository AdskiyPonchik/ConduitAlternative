<template>
  <main class="about-page">
    <header class="about-header">
      <div class="about-width">
        <p class="eyebrow">Project notes / Java &amp; Spring</p>
        <h1>Inside Conduit.</h1>
        <p class="intro">A publishing app with articles, comments, favorites and author subscriptions. Here is how its backend works.</p>
        <div class="about-actions">
          <AppLink name="global-feed" class="primary-link" aria-label="Open the app">Open the app <span aria-hidden="true">↗</span></AppLink>
          <a :href="repositoryUrl">Source on GitHub ↗</a>
        </div>
        <p class="stack-line">Java 21 · Spring Boot · PostgreSQL · Vue 3</p>
      </div>
    </header>

    <div class="about-width about-layout">
      <nav class="about-contents" aria-label="On this page">
        <span>On this page</span>
        <a href="#project">The project</a>
        <a href="#architecture">Architecture</a>
        <a href="#request-flow">Follow a request</a>
        <a href="#decisions">Decisions</a>
        <a href="#verification">Tests &amp; limits</a>
      </nav>

      <div class="about-content">
        <section id="project">
          <p class="section-number">01 / The project</p>
          <h2>Write, read and follow authors.</h2>
          <p>Conduit implements the RealWorld publishing API. Anyone can read articles and profiles. Signed-in users can publish, comment, save favorites and follow authors. Their personal feed contains articles from those authors.</p>
          <p>I use this project to learn Spring by rebuilding the backend of an earlier .NET application. The Vue client is adapted from an existing RealWorld implementation; the Java backend lives in the same repository.</p>
          <p class="project-context">In this portfolio, <strong>Telemetry Hub</strong> is the main Java project. Conduit focuses on a publishing API and its rules. Code covers LeetCode and Codewars solutions.</p>
        </section>

        <section id="architecture">
          <p class="section-number">02 / Architecture</p>
          <h2>One backend, one database.</h2>
          <p>Spring Boot runs one application. Code is grouped by feature: users, articles and comments. Each group contains its HTTP handlers, application logic and data access. Security is configured separately.</p>
          <ol class="architecture-flow" aria-label="Request path">
            <li><strong>Vue</strong><span>Interface</span></li>
            <li><strong>Security</strong><span>Authentication</span></li>
            <li><strong>Controller</strong><span>HTTP input</span></li>
            <li><strong>Service</strong><span>Rules &amp; transaction</span></li>
            <li><strong>Repository</strong><span>JPA &amp; SQL</span></li>
            <li><strong>PostgreSQL</strong><span>Stored data</span></li>
          </ol>
          <p>The controller passes validated input to a service. The service checks application rules and coordinates database work inside a transaction. Responses use DTOs instead of returning persistence entities directly.</p>
          <p>Comments have their own controller, service and repository. They still belong to an article: the database enforces that relationship, and the comment service checks the article before changing its comments.</p>
          <a :href="sourceUrl('src/main/java/de/conduit')">Browse the backend packages ↗</a>
        </section>

        <section id="request-flow">
          <p class="section-number">03 / Follow a request</p>
          <h2>What happens when you favorite an article?</h2>
          <p>Choose a case and move through its steps. This is a walkthrough based on the source code, with shortened excerpts. It does not send requests or change demo data.</p>
          <FavoriteWalkthrough />
        </section>

        <section id="decisions">
          <p class="section-number">04 / Decisions</p>
          <h2>Where the implementation gets specific.</h2>
          <div class="decisions">
            <article v-for="decision in decisions" :key="decision.title">
              <h3>{{ decision.title }}</h3>
              <p>{{ decision.body }}</p>
              <a :href="sourceUrl(decision.source)">{{ decision.link }} ↗</a>
            </article>
          </div>
        </section>

        <section id="verification">
          <p class="section-number">05 / Tests &amp; limits</p>
          <h2>What is checked. What is still limited.</h2>
          <p>Backend integration tests run against PostgreSQL through Testcontainers. They cover ownership rules, invalid tokens, combined list filters, repeated and concurrent favorites, subscriptions and cascading deletes. Frontend tests cover components and API session handling.</p>
          <div class="test-links">
            <a :href="sourceUrl('src/test/java/de/conduit/articles/ArticleFavoriteTests.java')">Concurrent favorites test ↗</a>
            <a :href="sourceUrl('src/test/java/de/conduit/security/AuthenticationTests.java')">Authentication tests ↗</a>
          </div>
          <ul class="limits-list">
            <li>JWTs expire after 15 minutes by default. There is no refresh-token flow. The client currently stores the token in localStorage.</li>
            <li>The feed follows subscriptions. There is no recommendation engine, full-text search or live push update.</li>
            <li>Favorite writes lock the article row. That keeps concurrent changes predictable, but also serializes these writes for a popular article.</li>
            <li>No load benchmark has been published. This page makes no throughput or latency claims.</li>
          </ul>
          <p class="source-reference">Backend source reference: <a :href="`${repositoryUrl}/tree/${sourceRef}`"><code>{{ sourceRef.slice(0, 7) }}</code></a>.</p>
        </section>

        <aside class="about-next">
          <div><strong>Try the behavior.</strong><p>Open an article, sign in and add it to your favorites.</p></div>
          <AppLink name="global-feed" aria-label="Go to Conduit">Go to Conduit →</AppLink>
        </aside>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import FavoriteWalkthrough from 'src/components/FavoriteWalkthrough.vue'
import { repositoryUrl, sourceRef, sourceUrl } from 'src/content/favorite-walkthrough'

const java = 'src/main/java/de/conduit/'
const decisions = [
  {
    title: 'Pagination before loading tags',
    body: 'The list query first selects a page of article IDs. A second query loads those articles and their tags. Joining multiple tags therefore does not shrink the page of articles. Author profiles, favorite counts and follow states are loaded in batches for that page.',
    source: `${java}articles/ArticleQueries.java`, link: 'ArticleQueries.java',
  },
  {
    title: 'Transactions around an operation',
    body: 'Service methods define transaction boundaries. A flush sends pending changes to the database; it does not commit them. A later failure can still roll the transaction back.',
    source: `${java}articles/DefaultArticleService.java`, link: 'DefaultArticleService.java',
  },
  {
    title: 'Rules enforced in the database too',
    body: 'Foreign keys connect articles, authors and comments. Composite keys prevent duplicate favorites and follows. A database check rejects following yourself. These constraints support the checks in the services.',
    source: 'src/main/resources/db/migration/V5__create_user_follows.sql', link: 'Follow constraints',
  },
  {
    title: 'Identity is separate from ownership',
    body: 'Spring Security validates the JWT. The article service separately checks whether that user owns the article before editing or deleting it. A valid token alone does not grant access to someone else’s work.',
    source: `${java}articles/DefaultArticleService.java`, link: 'Ownership check',
  },
  {
    title: 'JPA alongside explicit SQL',
    body: 'Entities use JPA repositories. Filtering and relationship changes also use parameterized SQL through EntityManager. Favorites and follows use PostgreSQL ON CONFLICT DO NOTHING for repeated requests.',
    source: `${java}articles/ArticleFavorites.java`, link: 'ArticleFavorites.java',
  },
  {
    title: 'Schema changes recorded as SQL',
    body: 'Flyway applies versioned migrations at startup. Hibernate validates the resulting schema instead of changing it automatically. The database structure can be recreated from the migration files.',
    source: 'src/main/resources/db/migration', link: 'Database migrations',
  },
]
</script>

<style scoped>
.about-page { color: #263a2d; background: #fcfdfb; }
.about-width { max-width: 1180px; margin: 0 auto; padding: 0 32px; }
.about-header { padding: 72px 0 40px; background: #edf3e8; border-top: 1px solid #dee7d8; border-bottom: 1px solid #d5dfce; }
.eyebrow, .section-number { font-size: .75rem; text-transform: uppercase; letter-spacing: .12em; font-weight: 600; color: #506a4d; }
.about-header h1 { font-size: clamp(3rem, 7vw, 5rem); font-weight: 600; line-height: 1.05; letter-spacing: -.05em; margin: 20px 0 24px; }
.intro { font-size: 1.3rem; max-width: 660px; line-height: 1.65; color: #4a5e4a; }
.about-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 24px; margin: 30px 0; }
.about-page a { color: #276532; text-decoration: underline; text-underline-offset: 4px; }
.about-actions .primary-link { display: inline-flex; gap: 32px; align-items: center; color: white; background: #264e30; padding: 12px 18px; border-radius: 5px; text-decoration: none; }
.stack-line { font-size: .85rem; color: #5a6a56; margin: 0; }
.about-layout { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 56px; padding-top: 56px; padding-bottom: 60px; }
.about-contents { position: sticky; top: 28px; align-self: start; display: flex; flex-direction: column; gap: 12px; font-size: .85rem; }
.about-contents span { color: #687865; margin-bottom: 8px; font-size: .72rem; letter-spacing: .08em; text-transform: uppercase; }
.about-contents a { text-decoration: none; color: #53604e; }
.about-contents a:hover { text-decoration: underline; }
.about-content { min-width: 0; }
section { scroll-margin-top: 28px; padding-bottom: 44px; margin-bottom: 44px; border-bottom: 1px solid #e0e6dc; }
section h2 { font-size: clamp(1.6rem, 3vw, 2rem); line-height: 1.3; letter-spacing: -.025em; margin-bottom: 22px; }
section p { font-size: 1.05rem; line-height: 1.8; color: #4c5c4e; }
section .section-number { font-size: .72rem; margin-bottom: 14px; }
section .project-context { border-left: 3px solid #adc69e; padding-left: 18px; font-size: .95rem; margin-top: 24px; }
.architecture-flow { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; list-style: none; padding: 0; margin: 28px 0; counter-reset: architecture; }
.architecture-flow li { border: 1px solid #d7e1d3; background: #f2f6ef; padding: 16px; border-radius: 6px; }
.architecture-flow li::before { counter-increment: architecture; content: '0' counter(architecture); display: block; font-size: .7rem; color: #61765b; margin-bottom: 10px; }
.architecture-flow strong { display: block; font-size: 1rem; }
.architecture-flow span { display: block; font-size: .8rem; color: #64745e; margin-top: 4px; }
.decisions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 32px; row-gap: 28px; }
.decisions h3 { font-size: 1.15rem; line-height: 1.4; margin-bottom: 10px; }
.decisions p { font-size: .95rem; margin-bottom: 10px; }
.decisions a { font-size: .85rem; }
.test-links { display: flex; flex-wrap: wrap; gap: 12px 24px; margin: 20px 0 28px; font-size: .9rem; }
.limits-list { list-style: disc; padding-left: 20px; color: #4c5c4e; }
.limits-list li { margin-bottom: 14px; line-height: 1.7; }
section .source-reference { font-size: .8rem; margin: 28px 0 0; }
.about-next { display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.about-next strong { font-size: 1.2rem; }
.about-next p { font-size: .9rem; color: #5e6b59; margin: 8px 0 0; }
.about-next a { flex-shrink: 0; }
@media (max-width: 850px) {
  .about-layout { grid-template-columns: minmax(0, 1fr); gap: 32px; }
  .about-contents { position: static; flex-direction: row; flex-wrap: wrap; gap: 12px 20px; }
  .about-contents span { width: 100%; margin-bottom: 0; }
}
@media (max-width: 550px) {
  .about-width { padding-left: 20px; padding-right: 20px; }
  .about-header { padding-top: 42px; }
  .intro { font-size: 1.1rem; }
  .about-layout { padding-top: 32px; }
  .architecture-flow { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .decisions { grid-template-columns: minmax(0, 1fr); }
  .about-next { align-items: start; flex-direction: column; }
}
</style>
