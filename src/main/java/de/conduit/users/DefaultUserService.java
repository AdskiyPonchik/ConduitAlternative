package de.conduit.users;


import de.conduit.security.TokenIssuer;
import de.conduit.users.dto.*;
import de.conduit.users.exception.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;


import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;


@Service
@Validated
public class DefaultUserService implements UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokens;
    private final String dummyPasswordHash;
    private final Clock clock;
    private final UserFollows follows;

    public DefaultUserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
                              TokenIssuer token, UserFollows follows) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tokens = token;
        this.follows = follows;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AuthenticatedUser register(RegisterUserCommand command) {
        String rawPassword = command.password();
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = User.register(UUID.randomUUID(), command.username(), command.email(), passwordHash, Instant.now(clock));

        if (users.findByUsernameIgnoreCase(user.getUsername()).isPresent()) {
            throw new UsernameAlreadyTakenException();
        }
        if (users.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new EmailAlreadyTakenException();
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translateUniqueConflict(exception);
        }

        return authenticatedUser(user);
    }


    private static RuntimeException translateUniqueConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && "23505".equals(violation.getSQLState())) {

                String constraintName = violation.getConstraintName();

                if ("ux_users_username_lower".equals(constraintName)) {
                    return new UsernameAlreadyTakenException(exception);
                }

                if ("ux_users_email_lower".equals(constraintName)) {
                    return new EmailAlreadyTakenException(exception);
                }
            }

            cause = cause.getCause();
        }
        return exception;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser login(LoginUserCommand command) {
        User user = users.findByEmailIgnoreCase(command.email()).orElse(null);

        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();

        boolean passwordMatches = passwordEncoder.matches(command.password(), storedHash);

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return authenticatedUser(user);
    }

    private AuthenticatedUser authenticatedUser(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getEmail(), tokens.issue(user.getId()), user.getBio(), user.getImageUrl(), user.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser(UUID userID) {
        User user = users.findById(userID).orElseThrow(CurrentUserNotFoundException::new);

        return new CurrentUser(user.getId(), user.getUsername(), user.getEmail(), user.getBio(), user.getImageUrl(), user.getRole());
    }

    @Override
    @Transactional
    public CurrentUser updateCurrentUser(UUID userID, UpdateUserCommand command) {
        User user = users.findById(userID).orElseThrow(CurrentUserNotFoundException::new);
        if (command.username() != null) {
            users.findByUsernameIgnoreCase(command.username()).filter(other -> !other.getId().equals(userID)).ifPresent(other -> {
                throw new UsernameAlreadyTakenException();
            });
        }
        if (command.email() != null) {
            users.findByEmailIgnoreCase(command.email()).filter(other -> !other.getId().equals(userID)).ifPresent(other -> {
                throw new EmailAlreadyTakenException();
            });
        }

        String passwordHash = command.password() == null ? null : passwordEncoder.encode(command.password());

        user.updateAccount(command.username(), command.email(), passwordHash, command.bio(), command.image(), Instant.now(clock));
        try {
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateUniqueConflict(exception);
        }

        return new CurrentUser(user.getId(), user.getUsername(), user.getEmail(), user.getBio(), user.getImageUrl(), user.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileView getProfile(String username, UUID viewerId) {
        User user = requireProfile(username);
        boolean following = follows.findFollowedIds(viewerId, Set.of(user.getId()))
                .contains(user.getId());
        return profile(user, following);
    }

    @Override
    @Transactional
    public ProfileView follow(UUID actorID, String username) {
        return changeFollow(actorID, username, true);
    }

    @Override
    @Transactional
    public ProfileView unfollow(UUID actorID, String username) {
        return changeFollow(actorID, username, false);
    }

    private ProfileView changeFollow(UUID actorID, String username, boolean add) {
        if (!users.existsById(actorID)) {
            throw new CurrentUserNotFoundException();
        }
        User target = requireProfile(username);
        if (actorID.equals(target.getId())) {
            throw new InvalidFollowException();
        }
        if (add) {
            follows.add(actorID, target.getId());
        } else {
            follows.remove(actorID, target.getId());
        }
        return profile(target, add);
    }


    private User requireProfile(String username) {
        return users.findByUsernameIgnoreCase(username.strip())
                .orElseThrow(ProfileNotFoundException::new);
    }

    private static ProfileView profile(User user, boolean following) {
        return new ProfileView(user.getUsername(), user.getBio(), user.getImageUrl(), following);
    }

}
