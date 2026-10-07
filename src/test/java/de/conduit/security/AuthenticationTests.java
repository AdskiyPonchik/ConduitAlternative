package de.conduit.security;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class AuthenticationTests {
    private static final String PASSWORD = "authentication-test-password-123";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenIssuer tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired Clock clock;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void registersLogsInAndLoadsCurrentUserWithBothTokenPrefixes() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        String registration = register(username, email, PASSWORD, 201);
        String registrationToken = JsonPath.read(registration, "$.user.token");

        User saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(decoder.decode(registrationToken).getSubject()).isEqualTo(saved.getId().toString());

        Map<String, Object> responseUser = JsonPath.read(registration, "$.user");
        assertThat(responseUser).containsOnlyKeys("username", "email", "token", "bio", "image", "role");

        String login = mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn().getResponse().getContentAsString();
        String loginToken = JsonPath.read(login, "$.user.token");

        for (String prefix : List.of("Token ", "Bearer ")) {
            mvc.perform(get("/api/user").header("Authorization", prefix + loginToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value(email))
                    .andExpect(jsonPath("$.user.token").value(loginToken));
        }
    }

    @Test
    void rejectsDuplicateEmailAndUsernameIgnoringCase() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        register(username, email, PASSWORD, 201);
        UUID originalId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        String rejectedEmail = uniqueName() + "@example.com";
        register(username.toUpperCase(Locale.ROOT), rejectedEmail, PASSWORD, 409);
        String rejectedUsername = uniqueName();
        register(rejectedUsername, email.toUpperCase(Locale.ROOT), PASSWORD, 409);

        assertThat(users.findByEmailIgnoreCase(rejectedEmail)).isEmpty();
        assertThat(users.findByUsernameIgnoreCase(rejectedUsername)).isEmpty();
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getId()).isEqualTo(originalId);
    }

    @Test
    void rejectsInvalidRegistrationWithoutSavingTheUser() throws Exception {
        for (String password : List.of("short", " ".repeat(20), "x".repeat(129))) {
            String username = uniqueName();
            String email = username + "@example.com";
            register(username, email, password, 400);
            assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        }

        String email = uniqueName() + "@example.com";
        register(" ", email, PASSWORD, 400);
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        String username = uniqueName();
        register(username, "not-an-email", PASSWORD, 400);
        assertThat(users.findByUsernameIgnoreCase(username)).isEmpty();

        for (String body : List.of("{", "{}", "{\"user\":null}")) {
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.body").isArray());
        }
    }

    @Test
    void rejectsWrongPasswordAndUnknownEmail() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        register(username, email, PASSWORD, 201);

        for (Map<String, String> credentials : List.of(
                Map.of("email", email, "password", "wrong-password-12345"),
                Map.of("email", uniqueName() + "@example.com", "password", PASSWORD))) {
            mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                            .content(payload(credentials)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors.body").isArray())
                    .andExpect(jsonPath("$.user").doesNotExist());
        }
    }

    @Test
    void requiresOneValidAuthorizationHeaderRatherThanATokenInTheBody() throws Exception {
        User user = user();
        String token = tokens.issue(user.getId());
        mvc.perform(get("/api/user")).andExpect(status().isUnauthorized());

        for (String header : List.of("Token not-a-jwt", "Basic " + token, "Bearer ")) {
            mvc.perform(get("/api/user").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/user").header("Authorization", "Token " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(Map.of("token", token, "bio", "Must not be saved"))))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(user.getId()).orElseThrow().getBio()).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        User user = user();
        JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(new byte[32], "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        String token = sign(otherEncoder, claims(user.getId()).build());

        mvc.perform(get("/api/user").header("Authorization", "Token " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidClaimsEvenWithACorrectSignature() throws Exception {
        User user = user();
        Instant now = Instant.now(clock);
        List<JwtClaimsSet> invalidClaims = List.of(
                claims(user.getId()).issuedAt(now.minusSeconds(600))
                        .notBefore(now.minusSeconds(600)).expiresAt(now.minusSeconds(300)).build(),
                claims(user.getId()).notBefore(now.plusSeconds(300)).build(),
                claims(user.getId()).issuer("another-issuer").build(),
                claims(user.getId()).audience(List.of("another-api")).build(),
                claims(user.getId()).subject("not-a-uuid").build(),
                claims(user.getId()).claims(values -> values.remove("sub")).build(),
                claims(user.getId()).claims(values -> values.remove("exp")).build(),
                claims(user.getId()).claims(values -> values.remove("nbf")).build()
        );

        // The control request proves the fixture is valid before checking rejected tokens.
        mvc.perform(get("/api/user").header("Authorization", "Token " + sign(encoder, claims(user.getId()).build())))
                .andExpect(status().isOk());
        for (JwtClaimsSet invalid : invalidClaims) {
            mvc.perform(get("/api/user").header("Authorization", "Token " + sign(encoder, invalid)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsAnOtherwiseValidTokenWhenTheUserNoLongerExists() throws Exception {
        User user = user();
        String token = tokens.issue(user.getId());
        mvc.perform(get("/api/user").header("Authorization", "Token " + token))
                .andExpect(status().isOk());

        users.deleteById(user.getId());

        mvc.perform(get("/api/user").header("Authorization", "Token " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.body").isArray());
    }

    private String register(String username, String email, String password, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(Map.of("username", username, "email", email, "password", password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private String payload(Map<String, ?> fields) {
        return JsonPath.parse(Map.of("user", fields)).jsonString();
    }

    private String uniqueName() {
        return "auth-" + UUID.randomUUID();
    }

    private User user() {
        String username = uniqueName();
        // These fixtures never log in: token validation only needs an existing user.
        return users.saveAndFlush(User.register(UUID.randomUUID(), username, username + "@example.com",
                "unused-test-password-hash", Instant.now(clock)));
    }

    private JwtClaimsSet.Builder claims(UUID userId) {
        Instant now = Instant.now(clock);
        return JwtClaimsSet.builder().issuer("conduit-backend").audience(List.of("conduit-api"))
                .subject(userId.toString()).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(900));
    }

    private String sign(JwtEncoder signingEncoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return signingEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
