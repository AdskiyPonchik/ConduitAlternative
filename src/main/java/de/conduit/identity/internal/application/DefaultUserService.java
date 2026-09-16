package de.conduit.identity.internal.application;


import de.conduit.identity.internal.domain.User;
import de.conduit.identity.internal.persistence.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;


import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


@Service
@Validated
public class DefaultUserService implements UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokens;
    private final String dummyPasswordHash;
    private final Clock clock;

    public DefaultUserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock, TokenIssuer token) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tokens = token;
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    @Override
    @Transactional
    public AuthenticatedUser register(RegisterUserCommand command) {
        String rawPassword = command.password();
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = User.register(
                UUID.randomUUID(),
                command.username(),
                command.email(),
                passwordHash,
                Instant.now(clock)
        );

        if (users.findByUsernameIgnoreCase(user.getUsername()).isPresent()) {
            throw new UsernameAlreadyTakenException();
        }
        if (users.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new EmailAlreadyTakenException();
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translateRegistrationConflict(exception);
        }

        return authenticatedUser(user);
    }


    private static RuntimeException translateRegistrationConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())) {

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
        User user = users.findByEmailIgnoreCase(command.email())
                .orElse(null);

        String storedHash = user == null
                ? dummyPasswordHash
                : user.getPasswordHash();

        boolean passwordMatches = passwordEncoder.matches(
                command.password(),
                storedHash
        );

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return authenticatedUser(user);
    }

    private AuthenticatedUser authenticatedUser(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                tokens.issue(user.getId()),
                user.getBio(),
                user.getImageUrl(),
                user.getRole()
        );
    }

}
