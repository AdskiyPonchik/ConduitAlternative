package de.conduit.identity.internal.application;

import de.conduit.identity.internal.domain.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        String email,
        String token,
        String bio,
        String imageUrl,
        UserRole role
) {
}
