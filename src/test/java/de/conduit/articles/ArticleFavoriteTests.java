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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class ArticleFavoriteTests {
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    @Autowired TokenIssuer tokens;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void favoriteAndUnfavoriteAreIdempotentForOwnersAndOtherUsers() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Favorite lifecycle", CREATED, List.of());

        for (int i = 0; i < 2; i++) {
            mutate(post(favoritePath(article)), owner, true, 1);
        }
        for (int i = 0; i < 2; i++) {
            mutate(post(favoritePath(article) + "/"), reader, true, 2);
        }
        assertThat(favoriteRows(article)).isEqualTo(2);

        for (int i = 0; i < 2; i++) {
            mutate(delete(favoritePath(article)), owner, false, 1);
        }
        read(article, reader, "", true, 1);
        for (int i = 0; i < 2; i++) {
            mutate(delete(favoritePath(article) + "/"), reader, false, 0);
        }
        assertThat(favoriteRows(article)).isZero();
    }

    @Test
    void publicArticleReadsUseTheViewerWhileCountsAndContentTimestampsStayShared() throws Exception {
        User owner = user();
        User reader = user();
        User stranger = user();
        Article article = article(owner, "Viewer state", CREATED, List.of("favorite-test"));
        String before = read(article, null, "", false, 0);
        String createdAt = JsonPath.read(before, "$.article.createdAt");
        String updatedAt = JsonPath.read(before, "$.article.updatedAt");

        mutate(post(favoritePath(article)), reader, true, 1);
        for (String slash : List.of("", "/")) {
            for (User viewer : List.of(owner, reader, stranger)) {
                String json = read(article, viewer, slash, viewer == reader, 1);
                assertUnchangedArticle(json, article, createdAt, updatedAt);
            }
            assertUnchangedArticle(read(article, null, slash, false, 1), article, createdAt, updatedAt);
        }

        mutate(delete(favoritePath(article)), reader, false, 0);
        assertUnchangedArticle(read(article, reader, "", false, 0), article, createdAt, updatedAt);
    }

    @Test
    void filteredPagesUseTheViewerWithoutLosingOrderingTagsOrTotalCount() throws Exception {
        User owner = user();
        User first = user();
        User second = user();
        String tag = "fav-" + UUID.randomUUID().toString().substring(0, 8);
        Article newest = article(owner, "Newest", CREATED.plusSeconds(3), List.of(tag));
        Article middle = article(owner, "Middle", CREATED.plusSeconds(2), List.of(tag, "extra-tag"));
        Article oldest = article(owner, "Oldest", CREATED.plusSeconds(1), List.of(tag));
        article(owner, "Different tag", CREATED.plusSeconds(4), List.of("unrelated"));
        article(user(), "Different author", CREATED.plusSeconds(5), List.of(tag));
        mutate(post(favoritePath(newest)), first, true, 1);
        mutate(post(favoritePath(middle)), first, true, 1);
        mutate(post(favoritePath(middle)), second, true, 2);
        mutate(post(favoritePath(oldest)), second, true, 1);

        for (String path : List.of("/api/articles", "/api/articles/")) {
            for (int viewerIndex = 0; viewerIndex < 3; viewerIndex++) {
                User viewer = viewerIndex == 0 ? null : viewerIndex == 1 ? first : second;
                MockHttpServletRequestBuilder request = get(path)
                        .param("author", "  " + owner.getUsername().toUpperCase() + "  ")
                        .param("tag", "  " + tag.toUpperCase() + "  ")
                        .param("limit", "2").param("offset", "1");
                authorize(request, viewer);
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.articlesCount").value(3))
                        .andExpect(jsonPath("$.articles", hasSize(2)))
                        .andExpect(jsonPath("$.articles[0].slug").value(middle.getSlug()))
                        .andExpect(jsonPath("$.articles[0].tagList", containsInAnyOrder(tag, "extra-tag")))
                        .andExpect(jsonPath("$.articles[0].favorited").value(viewer != null))
                        .andExpect(jsonPath("$.articles[0].favoritesCount").value(2))
                        .andExpect(jsonPath("$.articles[1].slug").value(oldest.getSlug()))
                        .andExpect(jsonPath("$.articles[1].favorited").value(viewer == second))
                        .andExpect(jsonPath("$.articles[1].favoritesCount").value(1));
            }
        }

        mvc.perform(get("/api/articles").param("author", owner.getUsername())
                        .param("tag", "unrelated").header("Authorization", token(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles[0].favorited").value(false))
                .andExpect(jsonPath("$.articles[0].favoritesCount").value(0));
    }

    @Test
    void updatingArticleReturnsRealFavoriteStateAndPreservesExistingFavorites() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Before update", CREATED, List.of());
        mutate(post(favoritePath(article)), reader, true, 1);

        mvc.perform(put("/api/articles/{slug}", article.getSlug())
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"title\":\"After update\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value("After update"))
                .andExpect(jsonPath("$.article.slug").value(article.getSlug()))
                .andExpect(jsonPath("$.article.favorited").value(false))
                .andExpect(jsonPath("$.article.favoritesCount").value(1));

        mutate(post(favoritePath(article)), owner, true, 2);
        mvc.perform(put("/api/articles/{slug}/", article.getSlug())
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"description\":\"After second update\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.favorited").value(true))
                .andExpect(jsonPath("$.article.favoritesCount").value(2));
        read(article, reader, "", true, 2);
        assertThat(favoriteRows(article)).isEqualTo(2);
    }

    @Test
    void mutationsRequireAuthenticationAndAnExistingActorAndArticle() throws Exception {
        User owner = user();
        Article article = article(owner, "Protected favorite", CREATED, List.of());
        String missingActorToken = "Token " + tokens.issue(UUID.randomUUID());
        for (String slash : List.of("", "/")) {
            String path = favoritePath(article) + slash;
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
            mvc.perform(delete(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("Authorization", missingActorToken))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path).header("Authorization", missingActorToken))
                    .andExpect(status().isUnauthorized());
            String missingPath = "/api/articles/missing-" + UUID.randomUUID() + "/favorite" + slash;
            mvc.perform(post(missingPath).header("Authorization", token(owner)))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(missingPath).header("Authorization", token(owner)))
                    .andExpect(status().isNotFound());
        }
        assertThat(favoriteRows(article)).isZero();
    }

    @Test
    void deletingArticleCascadesFavoritesWithoutDeletingUsersOrOtherFavorites() throws Exception {
        User owner = user();
        User reader = user();
        Article removed = article(owner, "Removed", CREATED, List.of("cascade-tag"));
        Article retained = article(owner, "Retained", CREATED, List.of());
        mutate(post(favoritePath(removed)), owner, true, 1);
        mutate(post(favoritePath(removed)), reader, true, 2);
        mutate(post(favoritePath(retained)), reader, true, 1);

        mvc.perform(delete("/api/articles/{slug}", removed.getSlug())
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk());
        assertThat(favoriteRows(removed)).isZero();
        assertThat(users.existsById(owner.getId())).isTrue();
        assertThat(users.existsById(reader.getId())).isTrue();
        read(retained, reader, "", true, 1);
        mvc.perform(post(favoritePath(removed)).header("Authorization", token(reader)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingFavoritingUserCascadesOnlyTheirFavorites() throws Exception {
        User owner = user();
        User reader = user();
        User otherReader = user();
        Article article = article(owner, "Retains article", CREATED, List.of());
        mutate(post(favoritePath(article)), reader, true, 1);
        mutate(post(favoritePath(article)), otherReader, true, 2);

        users.deleteById(reader.getId());
        assertThat(favoriteRows(article)).isEqualTo(1);
        read(article, otherReader, "", true, 1);
        read(article, null, "", false, 1);
    }

    @Test
    void swaggerDocumentsOptionalReadAuthenticationAndRequiredFavoriteAuthentication() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> schemes = JsonPath.read(document, "$.components.securitySchemes");
        for (String path : List.of("/api/articles", "/api/articles/",
                "/api/articles/{slug}", "/api/articles/{slug}/")) {
            List<Map<String, Object>> requirements = JsonPath.read(document,
                    "$['paths']['" + path + "']['get']['security']");
            assertThat(requirements).containsExactlyInAnyOrder(Map.of(), Map.of("tokenAuth", List.of()));
        }
        for (String path : List.of("/api/articles/{slug}/favorite", "/api/articles/{slug}/favorite/")) {
            for (String method : List.of("post", "delete")) {
                List<Map<String, Object>> requirements = JsonPath.read(document,
                        "$['paths']['" + path + "']['" + method + "']['security']");
                assertThat(requirements).containsExactly(Map.of("tokenAuth", List.of()));
                for (Map<String, Object> requirement : requirements) {
                    assertThat(schemes.keySet()).containsAll(requirement.keySet());
                }
            }
        }
    }

    @Test
    @Timeout(40)
    void simultaneousDuplicateFavoritesBothSucceedAndPersistOneRow() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Concurrent favorite", CREATED, List.of());
        String authorization = token(reader);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> favorite = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent requests did not start in time");
                }
                return mvc.perform(post(favoritePath(article)).header("Authorization", authorization))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(favorite);
            Future<Integer> second = executor.submit(favorite);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(favoriteRows(article)).isEqualTo(1);
        read(article, reader, "", true, 1);
    }

    private void mutate(MockHttpServletRequestBuilder request, User actor, boolean favorited, int count)
            throws Exception {
        mvc.perform(authorize(request, actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.slug").isString())
                .andExpect(jsonPath("$.article.author.username").isString())
                .andExpect(jsonPath("$.article.favorited").value(favorited))
                .andExpect(jsonPath("$.article.favoritesCount").value(count));
    }

    private String read(Article article, User viewer, String slash, boolean favorited, int count)
            throws Exception {
        return mvc.perform(authorize(get("/api/articles/" + article.getSlug() + slash), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.favorited").value(favorited))
                .andExpect(jsonPath("$.article.favoritesCount").value(count))
                .andReturn().getResponse().getContentAsString();
    }

    private void assertUnchangedArticle(String json, Article article, String createdAt, String updatedAt) {
        assertThat((String) JsonPath.read(json, "$.article.slug")).isEqualTo(article.getSlug());
        assertThat((String) JsonPath.read(json, "$.article.createdAt")).isEqualTo(createdAt);
        assertThat((String) JsonPath.read(json, "$.article.updatedAt")).isEqualTo(updatedAt);
        assertThat((String) JsonPath.read(json, "$.article.title")).isEqualTo(article.getTitle());
        assertThat((String) JsonPath.read(json, "$.article.body")).isEqualTo(article.getBody());
    }

    private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request, User viewer) {
        return viewer == null ? request : request.header("Authorization", token(viewer));
    }

    private String token(User user) {
        return "Token " + tokens.issue(user.getId());
    }

    private String favoritePath(Article article) {
        return "/api/articles/" + article.getSlug() + "/favorite";
    }

    private long favoriteRows(Article article) {
        return jdbc.queryForObject("select count(*) from article_favorites where article_id = ?",
                Long.class, article.getId());
    }

    // Fixtures commit before requests; the HTTP calls use their own persistence context and transactions.
    private User user() {
        String username = "fav-" + UUID.randomUUID();
        return users.saveAndFlush(User.register(UUID.randomUUID(), username,
                username + "@example.com", "unused-test-password-hash", CREATED));
    }

    private Article article(User author, String title, Instant createdAt, List<String> tags) {
        return articles.saveAndFlush(Article.create(UUID.randomUUID(), author.getId(), title,
                "Description", "Body", tags, createdAt));
    }
}
