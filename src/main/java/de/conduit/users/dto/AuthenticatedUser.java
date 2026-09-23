package de.conduit.users.dto;

import de.conduit.users.UserRole;

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
