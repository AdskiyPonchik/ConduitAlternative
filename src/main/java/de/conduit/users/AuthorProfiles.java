package de.conduit.users;

import java.util.Optional;
import java.util.UUID;

public interface AuthorProfiles {
    Optional<AuthorProfile> findById(UUID userId);

    record AuthorProfile(
            UUID id,
            String username,
            String bio,
            String image
    ) {
    }
}
