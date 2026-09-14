package de.conduit.identity.internal.persistence;

import de.conduit.PostgresTestConfiguration;
import de.conduit.identity.internal.domain.User;
import de.conduit.identity.internal.domain.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(PostgresTestConfiguration.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class UserRepositoryTests {
    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsUser() {

        UUID id = UUID.randomUUID();
        String testingName = "Daniel";
        String testingEmail = "daniel@example.com";
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        User user = User.register(
                id,
                testingName,
                testingEmail,
                "test-password-hash",
                now
        );

        users.saveAndFlush(user);
        entityManager.clear();

        User loaded = users.findById(id).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getUsername()).isEqualTo(testingName);
        assertThat(loaded.getEmail()).isEqualTo(testingEmail);
        assertThat(loaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void findsUserByEmailIgnoringCase(){
        UUID id = UUID.randomUUID();
        String testingName = "Daniel";
        String testingEmail = "daniel@example.com";
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        User user = User.register(
                id,
                testingName,
                testingEmail,
                "test-password-hash",
                now
        );
        users.saveAndFlush(user);
        entityManager.clear();

    }

    @Test
    void findsUserByUsernameIgnoringCase(){
        UUID id = UUID.randomUUID();
        String testingName = "Daniel";
        String testingEmail = "daniel@example.com";
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        User user = User.register(
                id,
                testingName,
                testingEmail,
                "test-password-hash",
                now
        );
        users.saveAndFlush(user);
        entityManager.clear();

        User loaded = users.findByUsernameIgnoreCase("dAnIeL").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCase(){
        UUID id = UUID.randomUUID();
        String testingName = "Daniel";
        String testingEmail = "daniel@example.com";
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        User user = User.register(
                id,
                testingName,
                testingEmail,
                "test-password-hash",
                now
        );
        users.saveAndFlush(user);

        User secondUser = User.register(
                UUID.randomUUID(),
                "DANIEL",
                "second@example.com",
                "test-password-hash",
                now
        );

        assertThatThrownBy(() -> users.saveAndFlush(secondUser)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
