package de.conduit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "spring.config.import=",
        // Deterministic test-only signing key, unrelated to local credentials.
        "conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestConfiguration.class)
class ConduitBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
