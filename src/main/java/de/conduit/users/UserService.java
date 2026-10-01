package de.conduit.users;

import de.conduit.users.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    CurrentUser updateCurrentUser(@NotNull UUID userID, @NotNull @Valid UpdateUserCommand command);

    ProfileView getProfile(@NotBlank String username, UUID viewerId);

    ProfileView follow(@NotNull UUID actorID, @NotBlank String username);

    ProfileView unfollow(@NotNull UUID actorID, @NotBlank String username);
}
