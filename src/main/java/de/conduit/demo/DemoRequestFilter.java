package de.conduit.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Single-process demo protection. Forwarded headers are deliberately not trusted.
public class DemoRequestFilter extends OncePerRequestFilter {
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_BUCKETS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int maxBodyBytes;
    private final int logins;
    private final int registrations;
    private final int writes;

    public DemoRequestFilter(Clock clock, int maxBodyBytes, int logins, int registrations, int writes) {
        if (maxBodyBytes < 1 || maxBodyBytes > 2_097_152 || logins < 1 || registrations < 1 || writes < 1) {
            throw new IllegalArgumentException("Demo limits must be positive; body limit must not exceed 2 MiB");
        }
        this.clock = clock;
        this.maxBodyBytes = maxBodyBytes;
        this.logins = logins;
        this.registrations = registrations;
        this.writes = writes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || !WRITES.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            reject(response, 413, "Request body is too large");
            return;
        }
        String path = request.getRequestURI().replaceFirst("/$", "");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? "user:" + jwt.getSubject() : "ip:" + request.getRemoteAddr();
        boolean login = path.equals("/api/users/login") && request.getMethod().equals("POST");
        boolean register = path.equals("/api/users") && request.getMethod().equals("POST");
        String key = login ? "login:" + request.getRemoteAddr()
                : register ? "register:" + request.getRemoteAddr() : "write:" + actor;
        long retry = acquire(key, login ? logins : register ? registrations : writes, register ? 3600 : 60);
        if (retry > 0) {
            response.setHeader("Retry-After", Long.toString(retry));
            reject(response, 429, "Too many requests. Please wait before trying again.");
            return;
        }
        // A bounded read also covers chunked requests and absent Content-Length.
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            reject(response, 413, "Request body is too large");
            return;
        }
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    private synchronized long acquire(String key, int limit, long durationSeconds) {
        long now = clock.instant().getEpochSecond();
        Window window = windows.get(key);
        if (window == null || window.endsAt <= now) {
            windows.entrySet().removeIf(entry -> entry.getValue().endsAt <= now);
            if (windows.size() >= MAX_BUCKETS) return 60;
            window = new Window(now + durationSeconds);
            windows.put(key, window);
        }
        if (window.used >= limit) return Math.max(1, window.endsAt - now);
        window.used++;
        return 0;
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"errors\":{\"body\":[\"" + message + "\"]}}");
    }

    private static class Window {
        final long endsAt;
        int used;
        Window(long endsAt) { this.endsAt = endsAt; }
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Demo JSON requests use blocking reads");
                }
            };
        }
    }
}
