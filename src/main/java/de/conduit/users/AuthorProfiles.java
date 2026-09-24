package de.conduit.users;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface AuthorProfiles {
    Optional<AuthorProfile> findById(UUID userId);

    Optional<AuthorProfile> findByUsername(String username);

    Map<UUID, AuthorProfile> findByIDs(Set<UUID> userIDs);

    record AuthorProfile(
            UUID id,
            String username,
            String bio,
            String image
    ) {
    }
}
