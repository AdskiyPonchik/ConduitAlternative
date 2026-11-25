package de.conduit.demo;

import de.conduit.PostgresTestConfiguration;
import de.conduit.articles.ArticleFavorites;
import de.conduit.users.UserFollows;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=", "conduit.demo.enabled=false",
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import({PostgresTestConfiguration.class, DemoDataTests.Fixtures.class})
class DemoDataTests {
    @Autowired DemoDataService data;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired PasswordEncoder passwords;
    @Autowired Clock clock;
    @Autowired ArticleFavorites favorites;
    @Autowired UserFollows follows;
    @Autowired TransactionTemplate transactions;

    @BeforeEach void emptyDedicatedTestDatabase() {
        for (String table : List.of("comments", "article_favorites", "user_follows", "article_tags", "articles", "users", "demo_state")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test void repeatedStartupPreservesVisitorChangesAndDoesNotDuplicateContent() {
        data.initialize(false);
        jdbc.update("update articles set title = 'Visitor edit'");
        data.initialize(false);
        assertThat(count("users")).isEqualTo(4);
        assertThat(count("articles")).isEqualTo(15);
        assertThat(count("comments")).isEqualTo(15);
        assertThat(count("user_follows")).isEqualTo(2);
        assertThat(count("article_favorites")).isEqualTo(23);
        assertThat(count("demo_state")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from articles where title = 'Visitor edit'", Long.class)).isEqualTo(15);
        String hash = jdbc.queryForObject("select password_hash from users where username = 'demo-reader'", String.class);
        assertThat(passwords.matches("demo-test-password-123", hash)).isTrue();
    }

    @Test void demoReadinessWaitsForCommittedSampleData() {
        var metadata = new DemoController(true, jdbc);
        assertThat(metadata.info().getStatusCode().value()).isEqualTo(503);
        data.initialize(false);
        assertThat(metadata.info().getStatusCode().value()).isEqualTo(200);
        assertThat(metadata.info().getBody().enabled()).isTrue();
    }

    @Test void explicitResetRestoresTheDatasetAndRemovesVisitorAccounts() {
        data.initialize(false);
        insertVisitor();
        jdbc.update("delete from articles");
        data.initialize(true);
        assertThat(count("users")).isEqualTo(4);
        assertThat(count("articles")).isEqualTo(15);
        assertThat(count("comments")).isEqualTo(15);
        assertThat(jdbc.queryForObject("select count(*) from users where username = 'visitor'", Long.class)).isZero();
    }

    @Test void evenResetRefusesAnUnmarkedDatabaseContainingUserData() {
        insertVisitor();
        assertThatThrownBy(() -> data.initialize(true)).hasMessageContaining("unmarked user data");
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("demo_state")).isZero();
    }

    @Test void databaseNameMustMatchTheExplicitConfirmation() {
        var wrongDatabase = new DemoDataService(entities, passwords, clock, favorites, follows,
                "another_demo", "demo-test-password-123");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> wrongDatabase.initialize(true)))
                .hasMessageContaining("dedicated database");
        assertThat(count("users")).isZero();
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    private void insertVisitor() {
        jdbc.update("""
                insert into users (id, username, email, password_hash, created_at, updated_at)
                values (gen_random_uuid(), 'visitor', 'visitor@example.test', 'test-hash', now(), now())
                """);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean DemoDataService demoFixture(EntityManager entities, PasswordEncoder passwords, Clock clock,
                                         ArticleFavorites favorites, UserFollows follows) {
            return new DemoDataService(entities, passwords, clock, favorites, follows,
                    "conduit_test_demo", "demo-test-password-123");
        }
    }
}
