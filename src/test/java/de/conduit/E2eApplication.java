package de.conduit;

import org.springframework.boot.SpringApplication;

// Test runtime only: the disposable database is owned by this process.
public class E2eApplication {
    public static void main(String[] args) {
        SpringApplication.from(ConduitBackendApplication::main)
                .with(PostgresTestConfiguration.class).run(args);
    }
}
