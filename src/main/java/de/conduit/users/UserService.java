package de.conduit.users;

import de.conduit.users.dto.AuthenticatedUser;
import de.conduit.users.dto.CurrentUser;
import de.conduit.users.dto.LoginUserCommand;
import de.conduit.users.dto.RegisterUserCommand;
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

    CurrentUser getCurrentUser(@NotNull UUID userId);

}
