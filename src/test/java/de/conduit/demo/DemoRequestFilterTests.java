package de.conduit.demo;

import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRequestFilterTests {
    private final MutableClock clock = new MutableClock();
    private final DemoRequestFilter filter = new DemoRequestFilter(clock, 16, 2, 1, 2);

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void loginLimitIncludesTrailingSlashAndResetsAfterTheWindow() throws Exception {
        assertThat(call("POST", "/api/users/login", "").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/users/login/", "").getStatus()).isEqualTo(200);
        var rejected = call("POST", "/api/users/login", "");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("\"errors\"");
        clock.now = clock.now.plusSeconds(60);
        assertThat(call("POST", "/api/users/login", "").getStatus()).isEqualTo(200);
    }

    @Test void registrationAndLoginHaveSeparateBudgets() throws Exception {
        assertThat(call("POST", "/api/users", "").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/users/", "").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/users/login", "").getStatus()).isEqualTo(200);
    }

    @Test void authenticatedWritesAreScopedToTheVerifiedUser() throws Exception {
        authenticate("first");
        assertThat(call("POST", "/api/articles", "").getStatus()).isEqualTo(200);
        assertThat(call("DELETE", "/api/articles/slug", "").getStatus()).isEqualTo(200);
        assertThat(call("PUT", "/api/user", "").getStatus()).isEqualTo(429);
        authenticate("second");
        assertThat(call("PUT", "/api/user", "").getStatus()).isEqualTo(200);
    }

    @Test void publicReadsDoNotConsumeTheWriteBudget() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(call("GET", "/api/articles", "").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/articles", "").getStatus()).isEqualTo(200);
    }

    @Test void knownOversizeBodyIsRejectedBeforeReachingTheController() throws Exception {
        assertThat(call("POST", "/api/articles", "x".repeat(17)).getStatus()).isEqualTo(413);
    }

    @Test void unknownLengthBodiesAreBoundedToo() throws Exception {
        var request = request("POST", "/api/articles", "x".repeat(17));
        var chunked = new HttpServletRequestWrapper(request) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        var response = new MockHttpServletResponse();
        AtomicInteger reached = new AtomicInteger();
        filter.doFilter(chunked, response, (req, res) -> reached.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reached).hasValue(0);
    }

    @Test void bufferedBodyIsStillAvailableToTheController() throws Exception {
        var request = request("POST", "/api/articles", "{\"article\":{}}");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"article\":{}}"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test void untrustedForwardedHeadersDoNotBypassTheIpLimit() throws Exception {
        var first = request("POST", "/api/users", "");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        filter.doFilter(first, new MockHttpServletResponse(), (req, res) -> {});
        var second = request("POST", "/api/users", "");
        second.addHeader("X-Forwarded-For", "2.2.2.2");
        var response = new MockHttpServletResponse();
        filter.doFilter(second, response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse call(String method, String path, String body) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(method, path, body), response, (req, res) -> {});
        return response;
    }

    private MockHttpServletRequest request(String method, String path, String body) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
