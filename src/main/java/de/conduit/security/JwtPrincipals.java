package de.conduit.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Reads the account ID from a verified JWT obtained via {@code @AuthenticationPrincipal}.
 * <p>
 * {@link JwtConfiguration} already rejects tokens whose {@code sub} is not a UUID, so parsing
 * here cannot fail for a token that reached a controller.
 */
public final class JwtPrincipals {

    private JwtPrincipals() {
    }

    /**
     * ID of the authenticated caller. Use on routes that {@link SecurityConfiguration} protects.
     */
    public static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * ID of the optional viewer on public routes, or {@code null} for an anonymous request.
     */
    public static UUID viewerId(Jwt jwt) {
        return jwt == null ? null : actorId(jwt);
    }
}
