package de.conduit.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    private final boolean enabled;
    private final JdbcTemplate jdbc;

    public DemoController(@Value("${conduit.demo.enabled:false}") boolean enabled, JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.jdbc = jdbc;
    }

    @GetMapping({"/api/demo", "/api/demo/"})
    public ResponseEntity<DemoInfo> info() {
        if (enabled && jdbc.queryForObject("select count(*) from demo_state", Long.class) == 0) {
            return ResponseEntity.status(503).body(new DemoInfo(false, "Demo data is initializing."));
        }
        return ResponseEntity.ok(new DemoInfo(enabled, enabled
                ? "Create a temporary account to publish, comment, favorite articles and follow authors. Demo data may be reset by the operator. Do not use personal information or a reused password."
                : ""));
    }

    public record DemoInfo(boolean enabled, String message) {}
}
