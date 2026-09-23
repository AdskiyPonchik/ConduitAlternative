package de.conduit.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultAuthorProfiles implements AuthorProfiles {
    private final UserRepository users;

    public DefaultAuthorProfiles(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthorProfile> findById(UUID userID) {
        return users.findById(userID).map(user -> new AuthorProfile(user.getId(),
                user.getUsername(), user.getBio(), user.getImageUrl()));
    }

}
