package de.conduit.articles;

import com.jayway.jsonpath.JsonPath;
import de.conduit.PostgresTestConfiguration;
import de.conduit.users.User;
import de.conduit.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class ArticleListTests {
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void usesActualOffsetAndDatabaseUuidOrderWhileKeepingTaglessArticles() throws Exception {
        User author = author("list-pagination");
        Article newest = article(author, 0x20000000L, "Newest", CREATED.plusSeconds(1), List.of());
        Article high = article(author, 0xf0000000L, "High UUID", CREATED, List.of("page-test"));
        Article middle = article(author, 0x70000000L, "Middle UUID", CREATED, List.of("page-test"));
        Article low = article(author, 0x10000000L, "Low UUID", CREATED, List.of("page-test"));

        mvc.perform(get("/api/articles").param("author", author.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(4))
                .andExpect(result -> {
                    List<String> slugs = JsonPath.read(result.getResponse().getContentAsString(), "$.articles[*].slug");
                    assertThat(slugs).containsExactly(newest.getSlug(), high.getSlug(), middle.getSlug(), low.getSlug());
                })
                .andExpect(jsonPath("$.articles[0].tagList").isEmpty());

        mvc.perform(get("/api/articles").param("author", author.getUsername())
                        .param("limit", "2").param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(4))
                .andExpect(result -> {
                    List<String> slugs = JsonPath.read(result.getResponse().getContentAsString(), "$.articles[*].slug");
                    assertThat(slugs).containsExactly(high.getSlug(), middle.getSlug());
                });
    }

    @Test
    void combinesNormalizedFiltersAndReturnsEveryTagOfMatchingArticles() throws Exception {
        User first = author("list-filters-first");
        User second = author("list-filters-second");
        String tag = "list-filter-java";
        Article match = article(first, 1, "Matching article", CREATED, List.of(tag, "list-extra-tag"));
        article(first, 2, "Different tag", CREATED, List.of("list-unrelated-tag"));
        article(second, 1, "Other author", CREATED, List.of(tag));

        mvc.perform(get("/api/articles").param("tag", "  LIST-FILTER-JAVA  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(2))
                .andExpect(jsonPath("$.articles", hasSize(2)));

        mvc.perform(get("/api/articles").param("tag", "  LIST-FILTER-JAVA  ")
                        .param("author", "  LIST-FILTERS-FIRST  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(1))
                .andExpect(jsonPath("$.articles", hasSize(1)))
                .andExpect(jsonPath("$.articles[0].slug").value(match.getSlug()))
                .andExpect(jsonPath("$.articles[0].author.username").value(first.getUsername()))
                .andExpect(jsonPath("$.articles[0].tagList", containsInAnyOrder(tag, "list-extra-tag")));

        mvc.perform(get("/api/articles").param("author", first.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesCount").value(2));
    }

    @Test
    void returnsEmptyPagesWithCorrectCountsForMissingAuthorsTagsAndLargeOffsets() throws Exception {
        User author = author("list-empty-pages");
        article(author, 1, "Only article", CREATED, List.of("list-empty-page-tag"));

        mvc.perform(get("/api/articles").param("author", "list-nonexistent-author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").isEmpty())
                .andExpect(jsonPath("$.articlesCount").value(0));
        mvc.perform(get("/api/articles").param("tag", "list-nonexistent-tag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").isEmpty())
                .andExpect(jsonPath("$.articlesCount").value(0));
        mvc.perform(get("/api/articles").param("author", author.getUsername())
                        .param("offset", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").isEmpty())
                .andExpect(jsonPath("$.articlesCount").value(1));
    }

    @Test
    void defaultsToTwentyItemsSupportsTrailingSlashAndTreatsBlankFiltersAsAbsent() throws Exception {
        User author = author("list-defaults");
        for (int i = 1; i <= 21; i++) {
            article(author, i, "Default page " + i, CREATED.plusSeconds(i), List.of());
        }
        for (String path : List.of("/api/articles", "/api/articles/")) {
            mvc.perform(get(path).param("author", author.getUsername()).param("tag", "  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articles", hasSize(20)))
                    .andExpect(jsonPath("$.articlesCount").value(21))
                    .andExpect(jsonPath("$.articles[0].title").value("Default page 21"))
                    .andExpect(jsonPath("$.articles[19].title").value("Default page 2"));
        }

        String unfiltered = mvc.perform(get("/api/articles").param("limit", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String blankFilters = mvc.perform(get("/api/articles").param("limit", "100")
                        .param("author", "  ").param("tag", "  "))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(blankFilters).isEqualTo(unfiltered);
    }

    @Test
    void rejectsInvalidAndOverflowingNumericParametersWithControlledErrors() throws Exception {
        for (String limit : List.of("0", "-1", "101", "word", "2147483648")) {
            mvc.perform(get("/api/articles").param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").isArray())
                    .andExpect(jsonPath("$.errors.body[0]").isString());
        }
        for (String offset : List.of("-1", "word", "2147483648")) {
            mvc.perform(get("/api/articles").param("offset", offset))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").isArray())
                    .andExpect(jsonPath("$.errors.body[0]").isString());
        }
    }

    @Test
    void keepsFeedAndMutationsProtectedAndSwaggerUsesExistingSecuritySchemes() throws Exception {
        mvc.perform(get("/api/articles/feed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/articles/feed/")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/articles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{}}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/articles/any-slug").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{}}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/articles/any-slug")).andExpect(status().isUnauthorized());

        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> schemes = JsonPath.read(document, "$.components.securitySchemes");
        for (String method : List.of("put", "delete")) {
            List<Map<String, Object>> requirements = JsonPath.read(document,
                    "$['paths']['/api/articles/{slug}']['" + method + "']['security']");
            assertThat(requirements).isNotEmpty();
            assertThat(requirements.getFirst()).containsKey("tokenAuth");
            for (Map<String, Object> requirement : requirements) {
                assertThat(schemes.keySet()).containsAll(requirement.keySet());
            }
        }
    }

    // Each save runs its own repository transaction; requests get no test-managed persistence context.
    private User author(String username) {
        return users.saveAndFlush(User.register(
                UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)),
                username, username + "@example.com", "unused-test-password-hash", CREATED));
    }

    private Article article(User author, long orderingPrefix, String title, Instant createdAt, List<String> tags) {
        String id = "%08x-0000-0000-0000-%012x".formatted(
                orderingPrefix, Integer.toUnsignedLong(author.getUsername().hashCode()));
        return articles.saveAndFlush(Article.create(
                UUID.fromString(id), author.getId(), title, "Description", "Body", tags, createdAt));
    }
}
