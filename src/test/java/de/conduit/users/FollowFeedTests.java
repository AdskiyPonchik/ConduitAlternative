package de.conduit.users;

import com.jayway.jsonpath.JsonPath;
import de.conduit.PostgresTestConfiguration;
import de.conduit.articles.Article;
import de.conduit.articles.ArticleRepository;
import de.conduit.security.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class FollowFeedTests {
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
    void followAndUnfollowAreIdempotentCaseInsensitiveAndDirectional() throws Exception {
        User first = user(), second = user(), stranger = user();
        second.updateProfile(second.getUsername(), "Public biography", "https://example.com/avatar.png", CREATED);
        users.saveAndFlush(second);

        for (String slash : List.of("", "/")) {
            String response = request(post("/api/profiles/" + second.getUsername().toUpperCase(Locale.ROOT)
                    + "/follow" + slash), first);
            Map<String, Object> profile = JsonPath.read(response, "$.profile");
            assertThat(profile).containsOnlyKeys("username", "bio", "image", "following")
                    .containsEntry("username", second.getUsername()).containsEntry("following", true)
                    .containsEntry("bio", "Public biography").containsEntry("image", "https://example.com/avatar.png");
        }
        assertThat(followRows(first, second)).isEqualTo(1);
        profile(second, first, true);
        profile(first, second, false);
        profile(second, stranger, false);
        profile(second, null, false);

        follow(second, first);
        profile(first, second, true);
        for (String slash : List.of("", "/")) {
            mvc.perform(delete(followPath(second) + slash).header("Authorization", auth(first)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(false));
        }
        assertThat(followRows(first, second)).isZero();
        assertThat(followRows(second, first)).isEqualTo(1);
        assertThat(users.findById(first.getId()).orElseThrow().getUpdatedAt()).isEqualTo(CREATED);
        assertThat(users.findById(second.getId()).orElseThrow().getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void followMutationsRequireExistingActorsAndTargetsAndRejectSelfFollowing() throws Exception {
        User actor = user(), target = user();
        String missingActor = "Token " + tokens.issue(UUID.randomUUID());
        for (String slash : List.of("", "/")) {
            for (boolean removing : List.of(false, true)) {
                MockHttpServletRequestBuilder anonymous = removing ? delete(followPath(target) + slash) : post(followPath(target) + slash);
                mvc.perform(anonymous).andExpect(status().isUnauthorized());
                MockHttpServletRequestBuilder absent = removing ? delete(followPath(target) + slash) : post(followPath(target) + slash);
                mvc.perform(absent.header("Authorization", missingActor)).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.errors.body").isArray());
                String missingPath = "/api/profiles/missing-" + UUID.randomUUID() + "/follow" + slash;
                MockHttpServletRequestBuilder missing = removing ? delete(missingPath) : post(missingPath);
                mvc.perform(missing.header("Authorization", auth(actor))).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.errors.body").isArray());
                String selfPath = "/api/profiles/" + actor.getUsername().toUpperCase(Locale.ROOT) + "/follow" + slash;
                MockHttpServletRequestBuilder self = removing ? delete(selfPath) : post(selfPath);
                mvc.perform(self.header("Authorization", auth(actor))).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.body").isArray());
            }
        }
        assertThat(followRows(actor, target)).isZero();
        assertThat(followRows(actor, actor)).isZero();
    }

    @Test
    void profileAndArticleReadsReportFollowingForTheirViewerAndKeepPublicReadsAvailable() throws Exception {
        User author = user(), reader = user(), stranger = user();
        Article article = article(author, "Viewer follows author", CREATED, List.of());
        follow(reader, author);
        for (String slash : List.of("", "/")) {
            for (User viewer : List.of(reader, author, stranger)) {
                boolean following = viewer == reader;
                mvc.perform(get("/api/profiles/" + author.getUsername().toUpperCase(Locale.ROOT) + slash)
                                .header("Authorization", auth(viewer)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(following));
                mvc.perform(get(articlePath(article) + slash).header("Authorization", auth(viewer)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.article.author.following").value(following));
                mvc.perform(get("/api/articles" + slash).param("author", author.getUsername())
                                .header("Authorization", auth(viewer)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(1))
                        .andExpect(jsonPath("$.articles[0].author.following").value(following));
            }
            mvc.perform(get("/api/profiles/" + author.getUsername() + slash))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(false));
            mvc.perform(get(articlePath(article) + slash))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.article.author.following").value(false));
            mvc.perform(get("/api/articles" + slash).param("author", author.getUsername()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.articles[0].author.following").value(false));
        }
    }

    @Test
    void commentFollowingUsesEachCommentAuthorAndTheReadingViewer() throws Exception {
        User owner = user(), followedCommenter = user(), otherCommenter = user(), reader = user();
        Article article = article(owner, "Mixed comment authors", CREATED, List.of());
        follow(reader, followedCommenter);
        follow(followedCommenter, owner);
        createComment(article, owner, "Owner comment");
        createComment(article, followedCommenter, "Followed comment");
        createComment(article, otherCommenter, "Other comment");
        createComment(article, reader, "Own comment");

        for (String slash : List.of("", "/")) {
            for (boolean signedIn : List.of(false, true)) {
                MockHttpServletRequestBuilder request = get(articlePath(article) + "/comments" + slash);
                if (signedIn) request.header("Authorization", auth(reader));
                String json = mvc.perform(request).andExpect(status().isOk())
                        .andExpect(jsonPath("$.comments", hasSize(4))).andReturn().getResponse().getContentAsString();
                List<Map<String, Object>> profiles = JsonPath.read(json, "$.comments[*].author");
                for (Map<String, Object> profile : profiles) {
                    assertThat(profile).containsOnlyKeys("username", "bio", "image", "following");
                    assertThat(profile.get("following"))
                            .isEqualTo(signedIn && followedCommenter.getUsername().equals(profile.get("username")));
                }
            }
        }
    }

    @Test
    void feedUsesOnlyOutgoingFollowsIncludesOldArticlesAndDefaultsToTwentyRows() throws Exception {
        User reader = user(), followed = user(), unfollowed = user(), otherReader = user();
        List<Article> expected = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            expected.add(article(followed, "Already published " + i, CREATED.plusSeconds(i), List.of()));
        }
        article(reader, "Reader own article", CREATED.plusSeconds(100), List.of());
        Article unrelated = article(unfollowed, "Favorite but not followed", CREATED.plusSeconds(101), List.of());
        request(post(articlePath(unrelated) + "/favorite"), reader);
        follow(unfollowed, reader);
        assertFeedEmpty(reader);
        follow(reader, followed);

        String json = request(get("/api/articles/feed"), reader);
        assertThat((Integer) JsonPath.read(json, "$.articlesCount")).isEqualTo(23);
        List<String> slugs = JsonPath.read(json, "$.articles[*].slug");
        assertThat(slugs).hasSize(20);
        assertThat(slugs.getFirst()).isEqualTo(expected.get(22).getSlug());
        assertThat(slugs.getLast()).isEqualTo(expected.get(3).getSlug());
        assertThat(JsonPath.<List<Boolean>>read(json, "$.articles[*].author.following")).containsOnly(true);
        assertFeedEmpty(otherReader);

        request(delete(followPath(followed)), reader);
        assertFeedEmpty(reader);
        mvc.perform(get(articlePath(expected.getFirst()))).andExpect(status().isOk());
        assertThat(followRows(unfollowed, reader)).isEqualTo(1);
    }

    @Test
    void feedSupportsArbitraryOffsetsStableUuidTiesAndFullCountsAfterTheLastPage() throws Exception {
        User reader = user(), firstAuthor = user(), secondAuthor = user();
        String idPrefix = UUID.randomUUID().toString().substring(0, 24);
        Article low = article(UUID.fromString(idPrefix + "000000000001"), firstAuthor, "Tie low", CREATED, List.of());
        Article high = article(UUID.fromString(idPrefix + "000000000003"), secondAuthor, "Tie high", CREATED, List.of());
        Article middle = article(UUID.fromString(idPrefix + "000000000002"), firstAuthor, "Tie middle", CREATED, List.of());
        Article newest = article(secondAuthor, "Newer timestamp", CREATED.plusSeconds(1), List.of());
        follow(reader, firstAuthor);
        follow(reader, secondAuthor);

        String all = request(get("/api/articles/feed/").param("limit", "100"), reader);
        assertThat(JsonPath.<List<String>>read(all, "$.articles[*].slug"))
                .containsExactly(newest.getSlug(), high.getSlug(), middle.getSlug(), low.getSlug());
        mvc.perform(get("/api/articles/feed").header("Authorization", auth(reader))
                        .param("limit", "2").param("offset", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(4))
                .andExpect(jsonPath("$.articles", hasSize(2)))
                .andExpect(jsonPath("$.articles[0].slug").value(high.getSlug()))
                .andExpect(jsonPath("$.articles[1].slug").value(middle.getSlug()));
        for (String offset : List.of("4", "100")) {
            mvc.perform(get("/api/articles/feed/").header("Authorization", auth(reader))
                            .param("limit", "1").param("offset", offset))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(4))
                    .andExpect(jsonPath("$.articles").isEmpty());
        }
    }

    @Test
    void feedIsAProtectedRouteWithValidatedPaginationAndSwaggerSecurity() throws Exception {
        User reader = user();
        for (String slash : List.of("", "/")) {
            mvc.perform(get("/api/articles/feed" + slash)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/articles/feed" + slash)
                            .header("Authorization", "Token " + tokens.issue(UUID.randomUUID())))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors.body").isArray());
            mvc.perform(get("/api/articles/feed" + slash).header("Authorization", auth(reader)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(0))
                    .andExpect(jsonPath("$.articles").isEmpty()).andExpect(jsonPath("$.article").doesNotExist());
        }
        Map<String, List<String>> invalidParameters = Map.of(
                "limit", List.of("0", "101", "-1", "word", "2147483648"),
                "offset", List.of("-1", "word", "2147483648"));
        for (Map.Entry<String, List<String>> entry : invalidParameters.entrySet()) {
            for (String invalid : entry.getValue()) {
                mvc.perform(get("/api/articles/feed").header("Authorization", auth(reader)).param(entry.getKey(), invalid))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").isArray());
            }
        }
        String document = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String slash : List.of("", "/")) {
            for (String pathAndMethod : List.of("/api/articles/feed" + slash + "|get",
                    "/api/profiles/{username}/follow" + slash + "|post",
                    "/api/profiles/{username}/follow" + slash + "|delete")) {
                String[] parts = pathAndMethod.split("\\|");
                List<Map<String, Object>> schemes = JsonPath.read(document,
                        "$['paths']['" + parts[0] + "']['" + parts[1] + "']['security']");
                assertThat(schemes).containsExactly(Map.of("tokenAuth", List.of()));
            }
        }
    }

    @Test
    void feedRetainsTagsFavoritesAndFollowingAcrossArticleMutationsWithoutChangingGlobalFilters() throws Exception {
        User author = user(), reader = user(), otherReader = user(), unrelatedAuthor = user();
        String tag = "follow-" + UUID.randomUUID().toString().substring(0, 8);
        Article followed = article(author, "Followed and favorited", CREATED, List.of(tag, "extra"));
        Article unrelated = article(unrelatedAuthor, "Unfollowed but matching global filters", CREATED.plusSeconds(1), List.of(tag));
        follow(reader, author);
        for (User viewer : List.of(reader, otherReader)) request(post(articlePath(followed) + "/favorite"), viewer);
        request(post(articlePath(unrelated) + "/favorite"), reader);

        mvc.perform(get("/api/articles/feed").header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(1))
                .andExpect(jsonPath("$.articles[0].slug").value(followed.getSlug()))
                .andExpect(jsonPath("$.articles[0].tagList", containsInAnyOrder(tag, "extra")))
                .andExpect(jsonPath("$.articles[0].favorited").value(true))
                .andExpect(jsonPath("$.articles[0].favoritesCount").value(2))
                .andExpect(jsonPath("$.articles[0].author.following").value(true));
        mvc.perform(get("/api/articles").param("tag", tag).param("favorited", reader.getUsername())
                        .header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(2))
                .andExpect(jsonPath("$.articles[0].slug").value(unrelated.getSlug()))
                .andExpect(jsonPath("$.articles[0].author.following").value(false))
                .andExpect(jsonPath("$.articles[1].author.following").value(true));
        for (boolean removing : List.of(true, false)) {
            MockHttpServletRequestBuilder request = removing ? delete(articlePath(followed) + "/favorite") : post(articlePath(followed) + "/favorite");
            mvc.perform(request.header("Authorization", auth(reader))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.article.author.following").value(true))
                    .andExpect(jsonPath("$.article.favorited").value(!removing));
        }
        mvc.perform(post("/api/articles").header("Authorization", auth(reader)).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("article", Map.of("title", "My own new article", "description", "Description", "body", "Body"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.article.author.following").value(false));
        mvc.perform(put(articlePath(followed)).header("Authorization", auth(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("article", Map.of("title", "Updated by its author"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.article.author.following").value(false));
    }

    @Test
    void renamingEitherUserPreservesFollowsFeedAndProfileFlags() throws Exception {
        User reader = user(), author = user();
        Article article = article(author, "Author renamed later", CREATED, List.of());
        follow(reader, author);
        String newAuthorName = "follow-renamed-" + UUID.randomUUID();
        String newReaderName = "follower-renamed-" + UUID.randomUUID();
        for (Map.Entry<User, String> entry : Map.of(author, newAuthorName, reader, newReaderName).entrySet()) {
            mvc.perform(put("/api/user").header("Authorization", auth(entry.getKey())).contentType(MediaType.APPLICATION_JSON)
                            .content(payload("user", Map.of("username", entry.getValue()))))
                    .andExpect(status().isOk());
        }
        assertThat(followRows(reader, author)).isEqualTo(1);
        mvc.perform(get("/api/profiles/" + newAuthorName).header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(true));
        mvc.perform(get("/api/articles/feed").header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(1))
                .andExpect(jsonPath("$.articles[0].slug").value(article.getSlug()))
                .andExpect(jsonPath("$.articles[0].author.username").value(newAuthorName))
                .andExpect(jsonPath("$.articles[0].author.following").value(true));
        mvc.perform(delete(followPath(author)).header("Authorization", auth(reader))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/profiles/" + newAuthorName + "/follow").header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(false));
        assertFeedEmpty(reader);
    }

    @Test
    void databaseRejectsInvalidRelationsAndCascadesWhenEitherUserIsDeleted() throws Exception {
        User first = user(), second = user(), third = user();
        follow(first, second);
        follow(second, first);
        follow(third, first);
        assertThatThrownBy(() -> insertFollow(first.getId(), second.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFollow(first.getId(), first.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFollow(UUID.randomUUID(), second.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFollow(first.getId(), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("delete from users where id = ?", first.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_follows where follower_id = ? or followed_id = ?",
                Long.class, first.getId(), first.getId())).isZero();
        assertThat(users.existsById(second.getId())).isTrue();
        assertThat(users.existsById(third.getId())).isTrue();
    }

    private void follow(User follower, User followed) throws Exception {
        mvc.perform(post(followPath(followed)).header("Authorization", auth(follower)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(true));
    }

    private void profile(User target, User viewer, boolean following) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/profiles/" + target.getUsername());
        if (viewer != null) request.header("Authorization", auth(viewer));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.profile.following").value(following));
    }

    private void createComment(Article article, User author, String body) throws Exception {
        mvc.perform(post(articlePath(article) + "/comments").header("Authorization", auth(author))
                        .contentType(MediaType.APPLICATION_JSON).content(payload("comment", Map.of("body", body))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comment.author.following").value(false));
    }

    private void assertFeedEmpty(User reader) throws Exception {
        mvc.perform(get("/api/articles/feed").header("Authorization", auth(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(0))
                .andExpect(jsonPath("$.articles").isEmpty());
    }

    private String request(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mvc.perform(request.header("Authorization", auth(actor))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void insertFollow(UUID follower, UUID followed) {
        jdbc.update("insert into user_follows (follower_id, followed_id) values (?, ?)", follower, followed);
    }

    private long followRows(User follower, User followed) {
        return jdbc.queryForObject("select count(*) from user_follows where follower_id = ? and followed_id = ?",
                Long.class, follower.getId(), followed.getId());
    }

    private String followPath(User target) { return "/api/profiles/" + target.getUsername() + "/follow"; }
    private String articlePath(Article article) { return "/api/articles/" + article.getSlug(); }
    private String auth(User user) { return "Token " + tokens.issue(user.getId()); }
    private String payload(String root, Map<String, Object> fields) { return JsonPath.parse(Map.of(root, fields)).jsonString(); }

    private User user() {
        String username = "follows-" + UUID.randomUUID();
        return users.saveAndFlush(User.register(UUID.randomUUID(), username, username + "@example.com",
                "unused-test-password-hash", CREATED));
    }

    private Article article(User author, String title, Instant createdAt, List<String> tags) {
        return article(UUID.randomUUID(), author, title, createdAt, tags);
    }

    private Article article(UUID id, User author, String title, Instant createdAt, List<String> tags) {
        return articles.saveAndFlush(Article.create(id, author.getId(), title, "Description", "Body", tags, createdAt));
    }
}
