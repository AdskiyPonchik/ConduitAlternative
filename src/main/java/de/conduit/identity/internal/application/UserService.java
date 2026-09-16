package de.conduit.identity.internal.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public interface UserService {
    AuthenticatedUser register(
            @NotNull @Valid RegisterUserCommand command
    );

    AuthenticatedUser login(
            @NotNull @Valid LoginUserCommand command
    );

}
