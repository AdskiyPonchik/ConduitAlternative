package de.conduit.articles;

import com.jayway.jsonpath.JsonPath;
import de.conduit.PostgresTestConfiguration;
import de.conduit.security.TokenIssuer;
import de.conduit.users.User;
import de.conduit.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class ArticleFavoriteFilterTests {
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    @Autowired TokenIssuer tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void combinesAllThreeNormalizedFiltersWithoutDuplicatesAndPreservesEveryTag() throws Exception {
        User author = user();
        User otherAuthor = user();
        User target = user();
        User anotherReader = user();
        String tag = "filter-" + UUID.randomUUID().toString().substring(0, 8);
        Article match = article(author, "All filters match", CREATED,
                List.of(tag, "another-tag", "third-tag"));
        Article wrongTag = article(author, "Wrong tag", CREATED.plusSeconds(1), List.of("wrong-tag"));
        Article wrongAuthor = article(otherAuthor, "Wrong author", CREATED.plusSeconds(2), List.of(tag));
        Article wrongFavorite = article(author, "Only another user likes this", CREATED.plusSeconds(3), List.of(tag));
        favorite(match, target);
        favorite(match, anotherReader);
        favorite(match, author);
        favorite(wrongTag, target);
        favorite(wrongAuthor, target);
        favorite(wrongFavorite, anotherReader);

        for (String path : List.of("/api/articles", "/api/articles/")) {
            mvc.perform(get(path)
                            .param("tag", "  " + tag.toUpperCase(Locale.ROOT) + "  ")
                            .param("author", "  " + author.getUsername().toUpperCase(Locale.ROOT) + "  ")
                            .param("favorited", "  " + target.getUsername().toUpperCase(Locale.ROOT) + "  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articlesCount").value(1))
                    .andExpect(jsonPath("$.articles", hasSize(1)))
                    .andExpect(jsonPath("$.articles[0].slug").value(match.getSlug()))
                    .andExpect(jsonPath("$.articles[0].tagList",
                            containsInAnyOrder(tag, "another-tag", "third-tag")))
                    .andExpect(jsonPath("$.articles[0].favoritesCount").value(3))
                    .andExpect(jsonPath("$.articles[0].favorited").value(false));
        }
    }

    @Test
    void usesExactOffsetAndDatabaseUuidOrderingWhileCountingAllMatchingArticles() throws Exception {
        User author = user();
        User target = user();
        User otherReader = user();
        Article newest = article(author, orderedId(0x20000000L), "Newest tagless", CREATED.plusSeconds(1), List.of());
        Article high = article(author, orderedId(0xf0000000L), "High UUID", CREATED, List.of("pagination"));
        Article middle = article(author, orderedId(0x70000000L), "Middle UUID", CREATED, List.of("pagination"));
        Article low = article(author, orderedId(0x10000000L), "Low UUID", CREATED, List.of("pagination"));
        article(author, "Not in this favorites list", CREATED.plusSeconds(2), List.of());
        for (Article article : List.of(newest, high, middle, low)) {
            favorite(article, target);
        }
        favorite(high, otherReader);

        String all = mvc.perform(get("/api/articles").param("favorited", target.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(4))
                .andExpect(jsonPath("$.articles[0].tagList").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertSlugs(all, newest, high, middle, low);

        String page = mvc.perform(get("/api/articles/").param("favorited", target.getUsername())
                        .param("limit", "2").param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(4))
                .andReturn().getResponse().getContentAsString();
        assertSlugs(page, high, middle);

        String last = mvc.perform(get("/api/articles").param("favorited", target.getUsername())
                        .param("limit", "2").param("offset", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(4))
                .andReturn().getResponse().getContentAsString();
        assertSlugs(last, low);

        for (String offset : List.of("4", "2147483647")) {
            mvc.perform(get("/api/articles").param("favorited", target.getUsername())
                            .param("limit", "2").param("offset", offset))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articlesCount").value(4))
                    .andExpect(jsonPath("$.articles").isEmpty());
        }
    }

    @Test
    void filterTargetIsIndependentOfTheAuthenticatedViewerAndAnonymousReadsStayPublic() throws Exception {
        User author = user();
        User target = user();
        User viewer = user();
        User extraReader = user();
        Article first = article(author, "Target only", CREATED.plusSeconds(2), List.of());
        Article shared = article(author, "Both target and viewer", CREATED.plusSeconds(1), List.of());
        Article viewerOnly = article(author, "Viewer only", CREATED.plusSeconds(3), List.of());
        favorite(first, target);
        favorite(first, extraReader);
        favorite(shared, target);
        favorite(shared, viewer);
        favorite(viewerOnly, viewer);

        for (String path : List.of("/api/articles", "/api/articles/")) {
            for (int viewerIndex = 0; viewerIndex < 3; viewerIndex++) {
                User currentViewer = viewerIndex == 0 ? null : viewerIndex == 1 ? viewer : target;
                MockHttpServletRequestBuilder request = get(path).param("favorited", target.getUsername());
                authorize(request, currentViewer);
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.articlesCount").value(2))
                        .andExpect(jsonPath("$.articles", hasSize(2)))
                        .andExpect(jsonPath("$.articles[0].slug").value(first.getSlug()))
                        .andExpect(jsonPath("$.articles[0].favorited").value(currentViewer == target))
                        .andExpect(jsonPath("$.articles[0].favoritesCount").value(2))
                        .andExpect(jsonPath("$.articles[1].slug").value(shared.getSlug()))
                        .andExpect(jsonPath("$.articles[1].favorited").value(currentViewer != null))
                        .andExpect(jsonPath("$.articles[1].favoritesCount").value(2));
            }
        }
    }

    @Test
    void unknownUsersAndExistingUsersWithoutFavoritesReturnAnEmptyResult() throws Exception {
        User author = user();
        User target = user();
        User emptyUser = user();
        Article article = article(author, "Existing favorite", CREATED, List.of());
        favorite(article, target);

        for (String username : List.of("missing-" + UUID.randomUUID(), emptyUser.getUsername())) {
            for (String offset : List.of("0", "1")) {
                mvc.perform(get("/api/articles").param("favorited", username)
                                .param("limit", "1").param("offset", offset)
                                .header("Authorization", token(target)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.articlesCount").value(0))
                        .andExpect(jsonPath("$.articles").isEmpty());
            }
        }
    }

    @Test
    void blankFavoriteFilterIsIgnoredForGuestsAndAuthenticatedViewers() throws Exception {
        User author = user();
        User target = user();
        Article marked = article(author, "Marked", CREATED.plusSeconds(1), List.of());
        article(author, "Unmarked", CREATED, List.of());
        favorite(marked, target);

        for (int viewerIndex = 0; viewerIndex < 2; viewerIndex++) {
            User viewer = viewerIndex == 0 ? null : target;
            String withoutFilter = mvc.perform(authorize(get("/api/articles")
                            .param("author", author.getUsername()), viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articlesCount").value(2))
                    .andReturn().getResponse().getContentAsString();
            for (String blank : List.of("", "   ", " \t ")) {
                String withBlankFilter = mvc.perform(authorize(get("/api/articles/")
                                .param("author", author.getUsername()).param("favorited", blank), viewer))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                assertThat(withBlankFilter).isEqualTo(withoutFilter);
            }
        }
    }

    @Test
    @Timeout(30)
    void removingFavoriteWaitsForAnExistingArticleRowLockBeforeChangingState() throws Exception {
        User author = user();
        User reader = user();
        Article article = article(author, "Locked favorite", CREATED, List.of());
        favorite(article, reader);
        String authorization = token(reader);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            int blockerPid;
            try (var statement = locker.createStatement();
                 var rows = statement.executeQuery("select pg_backend_pid()")) {
                assertThat(rows.next()).isTrue();
                blockerPid = rows.getInt(1);
            }
            try (var statement = locker.prepareStatement("select id from articles where id = ? for update")) {
                statement.setObject(1, article.getId());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }

            Future<String> removal = executor.submit(() -> mvc.perform(
                            delete("/api/articles/{slug}/favorite", article.getSlug())
                                    .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            boolean observedBlockedRequest = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !removal.isDone()) {
                observedBlockedRequest = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists (
                            select 1 from pg_stat_activity
                            where datname = current_database()
                              and wait_event_type = 'Lock'
                              and ? = any(pg_blocking_pids(pid))
                        )
                        """, Boolean.class, blockerPid));
                if (observedBlockedRequest) {
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(observedBlockedRequest)
                    .as("DELETE favorite must wait for the held article row lock")
                    .isTrue();
            assertThat(removal.isDone()).isFalse();
            assertThat(jdbc.queryForObject("""
                    select count(*) from article_favorites where article_id = ? and user_id = ?
                    """, Long.class, article.getId(), reader.getId())).isEqualTo(1);

            locker.rollback();
            String response = removal.get(10, TimeUnit.SECONDS);
            assertThat((Boolean) JsonPath.read(response, "$.article.favorited")).isFalse();
            assertThat(((Number) JsonPath.read(response, "$.article.favoritesCount")).longValue()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from article_favorites where article_id = ?",
                    Long.class, article.getId())).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertSlugs(String json, Article... expected) {
        List<String> slugs = JsonPath.read(json, "$.articles[*].slug");
        assertThat(slugs).containsExactly(java.util.Arrays.stream(expected).map(Article::getSlug).toArray(String[]::new));
    }

    private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request, User viewer) {
        return viewer == null ? request : request.header("Authorization", token(viewer));
    }

    private String token(User user) {
        return "Token " + tokens.issue(user.getId());
    }

    // Each fixture write commits before HTTP requests begin; no test-managed transaction masks visibility.
    private User user() {
        String username = "filter-" + UUID.randomUUID();
        return users.saveAndFlush(User.register(UUID.randomUUID(), username,
                username + "@example.com", "unused-test-password-hash", CREATED));
    }

    private Article article(User author, String title, Instant createdAt, List<String> tags) {
        return article(author, UUID.randomUUID(), title, createdAt, tags);
    }

    private Article article(User author, UUID id, String title, Instant createdAt, List<String> tags) {
        return articles.saveAndFlush(Article.create(id, author.getId(), title,
                "Description", "Body", tags, createdAt));
    }

    private UUID orderedId(long prefix) {
        return new UUID(prefix << 32, UUID.randomUUID().getLeastSignificantBits());
    }

    private void favorite(Article article, User user) {
        jdbc.update("insert into article_favorites (article_id, user_id) values (?, ?)",
                article.getId(), user.getId());
    }
}
