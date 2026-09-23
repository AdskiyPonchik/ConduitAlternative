package de.conduit.users.dto;

import de.conduit.users.UserRole;

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
