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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class CommentWorkflowTests {
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
    void createsAndPubliclyListsCommentsWithSafeAuthorProfilesAndPreservedMarkdown() throws Exception {
        User owner = user();
        User reader = user();
        reader.updateProfile(reader.getUsername(), "Reader biography", "https://example.com/avatar.png", CREATED);
        users.saveAndFlush(reader);
        Article article = article(owner, "Commented article");
        String body = "  # Heading\n\n    indented code\n\nTrailing spaces  \n";

        for (String slash : List.of("", "/")) {
            mvc.perform(get(path(article) + slash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comments").isEmpty());
        }

        String created = create(article, reader, body, "/");
        Object id = JsonPath.read(created, "$.comment.id");
        assertThat(id).isInstanceOf(Integer.class);
        assertThat((Integer) id).isPositive();
        String createdAt = JsonPath.read(created, "$.comment.createdAt");
        String updatedAt = JsonPath.read(created, "$.comment.updatedAt");
        assertThat(Instant.parse(createdAt)).isEqualTo(Instant.parse(updatedAt));
        assertThat((String) JsonPath.read(created, "$.comment.body")).isEqualTo(body);
        assertSafeAuthor(JsonPath.read(created, "$.comment.author"), reader);
        create(article, owner, "Author's own comment", "");

        for (String slash : List.of("", "/")) {
            String listed = mvc.perform(get(path(article) + slash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comments", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> comments = JsonPath.read(listed, "$.comments");
            Map<String, Object> readerComment = comments.stream()
                    .filter(comment -> id.equals(comment.get("id"))).findFirst().orElseThrow();
            assertThat(readerComment.get("body")).isEqualTo(body);
            assertThat(readerComment.get("createdAt")).isEqualTo(createdAt);
            assertThat(readerComment.get("updatedAt")).isEqualTo(updatedAt);
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) readerComment.get("author");
            assertSafeAuthor(profile, reader);
        }
        assertArticleTimestampUnchanged(article);

        mvc.perform(delete(path(article) + "/" + id + "/").header("Authorization", token(reader)))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertArticleTimestampUnchanged(article);
    }

    @Test
    void onlyTheCommentAuthorCanDeleteEvenWhenAnotherUserOwnsTheArticle() throws Exception {
        User owner = user();
        User commenter = user();
        User stranger = user();
        Article article = article(owner, "Ownership rules");
        int commentId = commentId(create(article, commenter, "A reader comment", ""));
        int ownerCommentId = commentId(create(article, owner, "Owner comment", ""));
        String url = path(article) + "/" + commentId;

        mvc.perform(delete(url).header("Authorization", token(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors.body").isArray());
        mvc.perform(delete(url + "/").header("Authorization", token(stranger)))
                .andExpect(status().isForbidden());
        assertThat(commentRows(article)).isEqualTo(2);

        mvc.perform(delete(url).header("Authorization", token(commenter)))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(delete(path(article) + "/" + ownerCommentId + "/")
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(commentRows(article)).isZero();
        mvc.perform(delete(url).header("Authorization", token(commenter)))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingArticlesAndWrongArticleCommentLinksReturn404AndInvalidIdsReturn400() throws Exception {
        User owner = user();
        User commenter = user();
        Article article = article(owner, "Original parent");
        Article other = article(owner, "Wrong parent");
        int id = commentId(create(article, commenter, "Stays on original article", ""));
        String missing = "/api/articles/missing-" + UUID.randomUUID() + "/comments";

        for (String slash : List.of("", "/")) {
            mvc.perform(get(missing + slash)).andExpect(status().isNotFound());
            mvc.perform(post(missing + slash).header("Authorization", token(commenter))
                            .contentType(MediaType.APPLICATION_JSON).content(payload("Missing article")))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(missing + "/" + id + slash).header("Authorization", token(commenter)))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(path(other) + "/" + id + slash).header("Authorization", token(commenter)))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(path(article) + "/2147483647" + slash).header("Authorization", token(commenter)))
                    .andExpect(status().isNotFound());
        }
        for (String invalid : List.of("word", "1.5", "2147483648")) {
            mvc.perform(delete(path(article) + "/" + invalid).header("Authorization", token(commenter)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").isArray());
        }
        assertThat(commentRows(article)).isEqualTo(1);
        assertThat(commentRows(other)).isZero();
    }

    @Test
    void mutationsRequireAuthenticationAndAnExistingUserWhileOnlyCommentListGetsArePublic() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Authentication");
        int id = commentId(create(article, reader, "Protected comment", ""));
        String missingUserToken = "Token " + tokens.issue(UUID.randomUUID());

        for (String slash : List.of("", "/")) {
            mvc.perform(post(path(article) + slash).contentType(MediaType.APPLICATION_JSON)
                            .content(payload("Anonymous")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path(article) + "/" + id + slash))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path(article) + slash).header("Authorization", missingUserToken)
                            .contentType(MediaType.APPLICATION_JSON).content(payload("Missing user")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path(article) + "/" + id + slash).header("Authorization", missingUserToken))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get(path(article))).andExpect(status().isOk());
        mvc.perform(get(path(article) + "/" + id)).andExpect(status().isUnauthorized());
        assertThat(commentRows(article)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPayloadsWithoutSavingAndAcceptsExactlyFiveThousandCharacters() throws Exception {
        User owner = user();
        Article article = article(owner, "Comment validation");
        for (String invalid : List.of("{", "null", "{}", "{\"comment\":null}",
                "{\"comment\":{}}", "{\"comment\":{\"body\":null}}",
                payload(""), payload(" \t\n "), payload("x".repeat(5001)))) {
            mvc.perform(post(path(article)).header("Authorization", token(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").isArray());
        }
        mvc.perform(post(path(article)).header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        assertThat(commentRows(article)).isZero();

        String body = " " + "x".repeat(4998) + " ";
        String response = create(article, owner, body, "");
        assertThat((String) JsonPath.read(response, "$.comment.body")).isEqualTo(body);
        assertThat(commentRows(article)).isEqualTo(1);
    }

    @Test
    void ordersByCreationTimeThenDescendingIntegerIdAndKeepsArticlesSeparate() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Sorted comments");
        Article other = article(owner, "Other article");
        int low = insertComment(article, reader, "Earlier lower ID", CREATED);
        int newest = insertComment(article, owner, "Newest timestamp", CREATED.plusSeconds(1));
        int high = insertComment(article, reader, "Earlier higher ID", CREATED);
        insertComment(other, owner, "Excluded other article", CREATED.plusSeconds(2));

        String response = mvc.perform(get(path(article)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(3)))
                .andExpect(jsonPath("$.comments[0].author.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.comments[1].author.username").value(reader.getUsername()))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(response, "$.comments[*].id");
        assertThat(ids).containsExactly(newest, high, low);
    }

    @Test
    void deletingArticleCascadesItsCommentsWithoutDeletingAuthorsOrOtherComments() throws Exception {
        User owner = user();
        User reader = user();
        Article removed = article(owner, "Delete article with comments");
        Article retained = article(owner, "Keep other article");
        create(removed, owner, "Owner comment", "");
        create(removed, reader, "Reader comment", "");
        int retainedId = commentId(create(retained, reader, "Retained comment", ""));

        mvc.perform(delete("/api/articles/{slug}", removed.getSlug()).header("Authorization", token(owner)))
                .andExpect(status().isOk());
        assertThat(commentRows(removed)).isZero();
        assertThat(users.existsById(owner.getId())).isTrue();
        assertThat(users.existsById(reader.getId())).isTrue();
        mvc.perform(get(path(removed))).andExpect(status().isNotFound());
        mvc.perform(get(path(retained))).andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].id").value(retainedId));
    }

    @Test
    void commentAuthorForeignKeyRestrictsUserDeletionUntilTheirCommentsAreDeleted() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Retain author");
        int id = commentId(create(article, reader, "Author must remain", ""));

        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", reader.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.existsById(reader.getId())).isTrue();
        assertThat(commentRows(article)).isEqualTo(1);
        mvc.perform(delete(path(article) + "/" + id).header("Authorization", token(reader)))
                .andExpect(status().isOk());
        assertThat(jdbc.update("delete from users where id = ?", reader.getId())).isEqualTo(1);
    }

    @Test
    void swaggerDocumentsJwtSecurityForBothMutationPathsAndSlashVariants() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> schemes = JsonPath.read(document, "$.components.securitySchemes");
        assertThat(schemes).containsKey("tokenAuth");
        for (String slash : List.of("", "/")) {
            List<Map<String, Object>> createSecurity = JsonPath.read(document,
                    "$['paths']['/api/articles/{slug}/comments" + slash + "']['post']['security']");
            List<Map<String, Object>> deleteSecurity = JsonPath.read(document,
                    "$['paths']['/api/articles/{slug}/comments/{commentId}" + slash + "']['delete']['security']");
            assertThat(createSecurity).containsExactly(Map.of("tokenAuth", List.of()));
            assertThat(deleteSecurity).containsExactly(Map.of("tokenAuth", List.of()));
        }
    }

    @Test
    @Timeout(30)
    void deletingCommentWaitsForTheArticleRowLockBeforeRemovingIt() throws Exception {
        User owner = user();
        User reader = user();
        Article article = article(owner, "Comment deletion lock");
        int id = commentId(create(article, reader, "Wait for article lock", ""));
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
            Future<Integer> deletion = executor.submit(() -> mvc.perform(delete(path(article) + "/" + id)
                            .header("Authorization", authorization))
                    .andReturn().getResponse().getStatus());
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !deletion.isDone()) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists (
                            select 1 from pg_stat_activity
                            where datname = current_database()
                              and wait_event_type = 'Lock'
                              and ? = any(pg_blocking_pids(pid))
                        )
                        """, Boolean.class, blockerPid));
                if (blocked) {
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(blocked).as("DELETE comment must wait for the article row lock").isTrue();
            assertThat(deletion.isDone()).isFalse();
            assertThat(commentRows(article)).isEqualTo(1);
            locker.rollback();
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(commentRows(article)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String create(Article article, User author, String body, String slash) throws Exception {
        return mvc.perform(post(path(article) + slash).header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.id").isNumber())
                .andExpect(jsonPath("$.comment.body").value(body))
                .andExpect(jsonPath("$.comment.author.username").value(author.getUsername()))
                .andReturn().getResponse().getContentAsString();
    }

    private void assertSafeAuthor(Map<String, Object> author, User expected) {
        assertThat(author).containsOnlyKeys("username", "bio", "image", "following");
        assertThat(author).containsEntry("username", expected.getUsername())
                .containsEntry("bio", expected.getBio())
                .containsEntry("image", expected.getImageUrl())
                .containsEntry("following", false);
    }

    private void assertArticleTimestampUnchanged(Article article) {
        Article stored = articles.findById(article.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(article.getCreatedAt());
        assertThat(stored.getUpdatedAt()).isEqualTo(article.getUpdatedAt());
        assertThat(stored.getSlug()).isEqualTo(article.getSlug());
    }

    private int commentId(String json) {
        return ((Number) JsonPath.read(json, "$.comment.id")).intValue();
    }

    private String path(Article article) {
        return "/api/articles/" + article.getSlug() + "/comments";
    }

    private String token(User user) {
        return "Token " + tokens.issue(user.getId());
    }

    private String payload(String body) {
        return JsonPath.parse(Map.of("comment", Map.of("body", body))).jsonString();
    }

    private long commentRows(Article article) {
        return jdbc.queryForObject("select count(*) from comments where article_id = ?", Long.class, article.getId());
    }

    private int insertComment(Article article, User author, String body, Instant createdAt) {
        return jdbc.queryForObject("""
                insert into comments (article_id, author_id, body, created_at, updated_at)
                values (?, ?, ?, ?, ?) returning id
                """, Integer.class, article.getId(), author.getId(), body,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    // Fixtures commit before HTTP requests; each request uses its own persistence context and transaction.
    private User user() {
        String username = "comments-" + UUID.randomUUID();
        return users.saveAndFlush(User.register(UUID.randomUUID(), username,
                username + "@example.com", "unused-test-password-hash", CREATED));
    }

    private Article article(User author, String title) {
        return articles.saveAndFlush(Article.create(UUID.randomUUID(), author.getId(), title,
                "Description", "Body", List.of(), CREATED));
    }
}
