package de.conduit.web;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature handlers inherit their shared {@code @ExceptionHandler} methods from {@link ApiExceptionHandler}.
 * Without them Spring would still answer 400, but without the RealWorld error body, so these tests check the body.
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class ApiExceptionHandlerTests {
    private static final String UNREADABLE = "Request body must contain valid JSON in the expected format";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(security).build();
    }

    @Test
    void unreadableBodyUsesErrorEnvelopeForEveryFeature() throws Exception {
        String token = register();

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body[0]").value(UNREADABLE));

        mvc.perform(post("/api/articles")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body[0]").value(UNREADABLE));

        mvc.perform(post("/api/articles/{slug}/comments", "any-slug")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body[0]").value(UNREADABLE));
    }

    @Test
    void requestValidationListsFieldErrors() throws Exception {
        String token = register();

        mvc.perform(post("/api/articles/{slug}/comments", "any-slug")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body", hasSize(1)))
                .andExpect(jsonPath("$.errors.body[0]").value(startsWith("comment: ")));
    }

    @Test
    void featureSpecificTypeMismatchMessagesStayScoped() throws Exception {
        String token = register();

        mvc.perform(get("/api/articles").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body[0]")
                        .value("Query parameters have invalid values: limit and offset must be integers"));

        mvc.perform(delete("/api/articles/{slug}/comments/{id}", "any-slug", "abc")
                        .header("Authorization", "Token " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body[0]").value("Comment id must be a valid integer"));
    }

    private String register() throws Exception {
        String username = "errors" + UUID.randomUUID().toString().substring(0, 12);
        String json = """
                {"user":{"username":"%s","email":"%s@example.com","password":"error-test-password-123"}}
                """.formatted(username, username);
        String response = mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.user.token");
    }
}
