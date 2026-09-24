package de.conduit.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DefaultAuthorProfiles implements AuthorProfiles {
    private final UserRepository users;

    public DefaultAuthorProfiles(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<AuthorProfile> findById(UUID userID) {
        return users.findById(userID).map(user -> new AuthorProfile(user.getId(),
                user.getUsername(), user.getBio(), user.getImageUrl()));
    }

    @Override
    public Optional<AuthorProfile> findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username).map(DefaultAuthorProfiles::view);
    }

    @Override
    public Map<UUID, AuthorProfile> findByIDs(Set<UUID> userIDs) {
        if (userIDs.isEmpty()) {
            return Map.of();
        }

        return users.findAllById(userIDs).stream().collect(Collectors.toUnmodifiableMap(
                User::getId, DefaultAuthorProfiles::view
        ));
    }

    private static AuthorProfile view(User user) {
        return new AuthorProfile(user.getId(), user.getUsername(), user.getBio(), user.getImageUrl());
    }

}
