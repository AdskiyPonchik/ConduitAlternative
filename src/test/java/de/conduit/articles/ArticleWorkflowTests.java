package de.conduit.articles;

import com.jayway.jsonpath.JsonPath;
import de.conduit.PostgresTestConfiguration;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class ArticleWorkflowTests {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(security).build();
    }

    @Test
    void createsReadsAndListsTagsThenUpdatesOnlySuppliedFields() throws Exception {
        Fixture fixture = createArticle();
        String initial = mvc.perform(get("/api/articles/{slug}", fixture.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.author.username").value(fixture.username()))
                .andExpect(jsonPath("$.article.tagList[0]").value(fixture.tag()))
                .andReturn().getResponse().getContentAsString();
        String createdAt = JsonPath.read(initial, "$.article.createdAt");

        mvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasItem(fixture.tag())));

        mvc.perform(put("/api/articles/{slug}", fixture.slug())
                        .header("Authorization", "Token " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"title\":\"Updated title\",\"description\":null,\"tagList\":[\"ignored\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value("Updated title"))
                .andExpect(jsonPath("$.article.description").value("Description"))
                .andExpect(jsonPath("$.article.body").value("# Body\n\nContent"))
                .andExpect(jsonPath("$.article.slug").value(fixture.slug()))
                .andExpect(jsonPath("$.article.createdAt").value(createdAt))
                .andExpect(jsonPath("$.article.tagList[0]").value(fixture.tag()));

        mvc.perform(get("/api/articles/{slug}", fixture.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value("Updated title"));
    }

    @Test
    void deniesOtherUsersAndRejectsInvalidContentWithoutChangingTheArticle() throws Exception {
        Fixture fixture = createArticle();
        String otherToken = register("other" + UUID.randomUUID());
        String url = "/api/articles/" + fixture.slug();

        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"article\":{}}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(url)).andExpect(status().isUnauthorized());
        mvc.perform(put(url).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"title\":\"Not allowed\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors.body").isArray());
        mvc.perform(delete(url).header("Authorization", "Token " + otherToken))
                .andExpect(status().isForbidden());

        mvc.perform(put(url).header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"title\":\"Must not be saved\",\"body\":\"   \"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(url).header("Authorization", "Token " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").isArray());
        mvc.perform(put(url).header("Authorization", "Token " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value("Original title"));
    }

    @Test
    void ownerDeletionRemovesArticleAndItsTagsAndMissingArticlesReturn404() throws Exception {
        Fixture fixture = createArticle();
        String url = "/api/articles/" + fixture.slug();
        mvc.perform(delete(url).header("Authorization", "Token " + fixture.token()))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tags")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", not(hasItem(fixture.tag()))));
        mvc.perform(delete(url).header("Authorization", "Token " + fixture.token()))
                .andExpect(status().isNotFound());
        mvc.perform(put(url).header("Authorization", "Token " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"article\":{}}"))
                .andExpect(status().isNotFound());
    }

    private Fixture createArticle() throws Exception {
        String id = UUID.randomUUID().toString();
        String username = "owner" + id;
        String token = register(username);
        String tag = "tag" + id.substring(0, 8);
        String json = """
                {"article":{"title":"Original title","description":"Description",
                "body":"# Body\\n\\nContent","tagList":["%s"," %s "]}}
                """.formatted(tag.toUpperCase(), tag);
        String response = mvc.perform(post("/api/articles")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Fixture(token, JsonPath.read(response, "$.article.slug"), username, tag);
    }

    private String register(String username) throws Exception {
        String json = """
                {"user":{"username":"%s","email":"%s@example.com","password":"article-test-password-123"}}
                """.formatted(username, username);
        String response = mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.user.token");
    }

    private record Fixture(String token, String slug, String username, String tag) {
    }
}
