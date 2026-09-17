package de.conduit.identity.internal.application;

import de.conduit.identity.internal.domain.UserRole;

import java.util.UUID;


public record CurrentUser(
        UUID id,
        String username,
        String email,
        String bio,
        String imageUrl,
        UserRole role
) {
}
