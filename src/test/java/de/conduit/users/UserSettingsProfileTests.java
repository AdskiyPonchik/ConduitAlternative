package de.conduit.users;

import com.jayway.jsonpath.JsonPath;
import de.conduit.PostgresTestConfiguration;
import de.conduit.articles.Article;
import de.conduit.articles.ArticleRepository;
import de.conduit.comments.Comment;
import de.conduit.comments.CommentRepository;
import de.conduit.security.TokenIssuer;
import de.conduit.users.dto.UpdateUserCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class UserSettingsProfileTests {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final String PASSWORD = "old-password-test-12345";
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    @Autowired CommentRepository comments;
    @Autowired TokenIssuer tokens;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void publicProfileHasOnlyPublicFieldsAndSupportsCaseAndTrailingSlash() throws Exception {
        User user = user();
        user.updateProfile(user.getUsername(), "Biography", "https://example.com/avatar.png", CREATED);
        users.saveAndFlush(user);
        for (String slash : List.of("", "/")) {
            String json = mvc.perform(get("/api/profiles/" + user.getUsername().toUpperCase(java.util.Locale.ROOT) + slash))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Map<String,Object> profile = JsonPath.read(json,"$.profile");
            assertThat(profile).containsOnlyKeys("username","bio","image","following")
                    .containsEntry("username",user.getUsername()).containsEntry("bio","Biography")
                    .containsEntry("image","https://example.com/avatar.png").containsEntry("following",false);
        }
        mvc.perform(get("/api/profiles/missing-"+UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errors.body").isArray());
    }

    @Test
    void updatesSettingsAndCredentialsWhileReturningThePresentedToken() throws Exception {
        User user = user();
        String token = tokens.issue(user.getId());
        String username = "renamed-"+UUID.randomUUID();
        String email = username+"@example.com";
        String password = "  new-password-with-spaces-123  ";
        String json = mvc.perform(put("/api/user/").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(Map.of("username","  "+username+"  ","email","  "+email.toUpperCase(java.util.Locale.ROOT)+"  ",
                                "password",password,"bio","  Updated bio  ","image"," https://example.com/new.png "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.email").value(email)).andExpect(jsonPath("$.user.bio").value("Updated bio"))
                .andExpect(jsonPath("$.user.image").value("https://example.com/new.png"))
                .andExpect(jsonPath("$.user.role").value("User")).andExpect(jsonPath("$.user.token").value(token))
                .andReturn().getResponse().getContentAsString();
        Map<String,Object> response = JsonPath.read(json,"$.user");
        assertThat(response).containsOnlyKeys("username","email","bio","image","role","token");
        User saved=users.findById(user.getId()).orElseThrow();
        assertThat(passwords.matches(password,saved.getPasswordHash())).isTrue();
        assertThat(passwords.matches(password.strip(),saved.getPasswordHash())).isFalse();
        login(email,password,200);
        login(email,PASSWORD,401);
        login(user.getEmail(),PASSWORD,401);
        mvc.perform(get("/api/user").header("Authorization","Token "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(username));
    }

    @Test
    void missingAndNullFieldsPreserveValuesWhileEmptyBioAndImageClearThem() throws Exception {
        User user=user();
        user.updateProfile(user.getUsername(),"Existing bio","https://example.com/old.png",CREATED);
        users.saveAndFlush(user);
        String oldHash=user.getPasswordHash();
        for(String data:List.of("{\"user\":{}}","{\"user\":{\"username\":null,\"email\":null,\"password\":null,\"bio\":null,\"image\":null}}")) {
            mvc.perform(put("/api/user").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content(data))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.bio").value("Existing bio"));
            User saved=users.findById(user.getId()).orElseThrow();
            assertThat(saved.getUpdatedAt()).isEqualTo(CREATED);
            assertThat(saved.getPasswordHash()).isEqualTo(oldHash);
        }
        update(user,Map.of("bio","  ","image",""),200);
        User saved=users.findById(user.getId()).orElseThrow();
        assertThat(saved.getBio()).isEmpty();
        assertThat(saved.getImageUrl()).isEmpty();
        assertThat(saved.getEmail()).isEqualTo(user.getEmail());
        assertThat(saved.getPasswordHash()).isEqualTo(oldHash);
    }

    @Test
    void permitsOwnNamesButRejectsCaseInsensitiveConflictsWithoutPartialChanges() throws Exception {
        User first=user(), second=user();
        update(first,Map.of("username",first.getUsername().toUpperCase(java.util.Locale.ROOT),
                "email",first.getEmail().toUpperCase(java.util.Locale.ROOT)),200);
        for(Map<String,Object> request:List.<Map<String,Object>>of(
                Map.of("username",second.getUsername().toUpperCase(java.util.Locale.ROOT),"bio","Must not save"),
                Map.of("email",second.getEmail().toUpperCase(java.util.Locale.ROOT),"password","new-but-rejected-12345"))) {
            update(first,request,409);
        }
        User saved=users.findById(first.getId()).orElseThrow();
        assertThat(saved.getBio()).isEmpty();
        assertThat(saved.getEmail()).isEqualTo(first.getEmail());
        assertThat(saved.getPasswordHash()).isEqualTo(first.getPasswordHash());
    }

    @Test
    void rejectsInvalidRequestsAndPreservesRoleAndOtherUsers() throws Exception {
        User user=user(), other=user();
        for(Map<String,Object> fields:List.<Map<String,Object>>of(
                Map.of("username"," "),Map.of("username","x".repeat(101)),
                Map.of("email",""),Map.of("email","not-an-email"),
                Map.of("password",""),Map.of("password"," ".repeat(20)),Map.of("password","short"),
                Map.of("password","x".repeat(129)),Map.of("bio","x".repeat(301)),Map.of("image","x".repeat(2049)))) {
            update(user,fields,400);
        }
        for(String data:List.of("{","{}","{\"user\":null}")) {
            mvc.perform(put("/api/user").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content(data))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").isArray());
        }
        update(user,Map.of("role","Admin","id",other.getId().toString(),"bio","Own bio"),200);
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
        assertThat(users.findById(other.getId()).orElseThrow().getBio()).isEmpty();
        assertThat(users.findById(user.getId()).orElseThrow().getUsername()).isEqualTo(user.getUsername());
    }

    @Test
    void updateRequiresAuthenticationAndAnExistingUserAndSwaggerNamesTheScheme() throws Exception {
        for(String slash:List.of("","/")) {
            mvc.perform(put("/api/user"+slash).contentType(MediaType.APPLICATION_JSON).content("{\"user\":{}}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(put("/api/user"+slash).header("Authorization","Token "+tokens.issue(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"user\":{}}"))
                    .andExpect(status().isUnauthorized());
        }
        String document=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String,Object>> security=JsonPath.read(document,"$['paths']['/api/user']['put']['security']");
        assertThat(security).containsExactly(Map.of("tokenAuth",List.of()));
    }

    @Test
    void renamedUserAppearsOnExistingArticlesCommentsAndFavoriteFilters() throws Exception {
        User user=user();
        Article article=articles.saveAndFlush(Article.create(UUID.randomUUID(),user.getId(),"Profile rename","Description","Body",List.of(),CREATED));
        comments.saveAndFlush(Comment.create(article.getId(),user.getId(),"An existing comment",CREATED));
        mvc.perform(post("/api/articles/"+article.getSlug()+"/favorite").header("Authorization",auth(user)))
                .andExpect(status().isOk());
        String newName="new-"+UUID.randomUUID();
        update(user,Map.of("username",newName,"bio","Renamed bio"),200);
        mvc.perform(get("/api/articles/"+article.getSlug()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.article.author.username").value(newName));
        mvc.perform(get("/api/articles/"+article.getSlug()+"/comments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].author.username").value(newName));
        mvc.perform(get("/api/articles").param("author",newName).param("favorited",newName))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(1));
        mvc.perform(get("/api/articles").param("author",user.getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articlesCount").value(0));
        mvc.perform(get("/api/profiles/"+user.getUsername())).andExpect(status().isNotFound());
    }

    @Test
    void passwordUsesCodePointLimitsAndIsRedactedInCommandText() throws Exception {
        User user=user();
        String password="🔐".repeat(15);
        update(user,Map.of("password",password),200);
        login(user.getEmail(),password,200);
        update(user,Map.of("password","🔐".repeat(14)),400);
        assertThat(new UpdateUserCommand(null,null,password,null,null).toString())
                .doesNotContain(password).contains("<redacted>");
    }

    private void update(User user,Map<String,Object> fields,int status) throws Exception {
        mvc.perform(put("/api/user").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content(payload(fields)))
                .andExpect(status().is(status));
    }
    private String payload(Map<String,Object> fields) { return JsonPath.parse(Map.of("user",fields)).jsonString(); }
    private String auth(User user) { return "Token "+tokens.issue(user.getId()); }
    private void login(String email,String password,int status) throws Exception {
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(Map.of("email",email,"password",password))))
                .andExpect(status().is(status));
    }
    private User user() {
        String name="settings-"+UUID.randomUUID();
        return users.saveAndFlush(User.register(UUID.randomUUID(),name,name+"@example.com",passwords.encode(PASSWORD),CREATED));
    }
}
