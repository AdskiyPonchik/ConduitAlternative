package de.conduit.users;

import de.conduit.users.dto.AuthenticatedUser;
import de.conduit.users.dto.LoginUserCommand;
import de.conduit.users.dto.RegisterUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import de.conduit.users.dto.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;


import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Users")
@SecurityScheme(
        name = "tokenAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "Authorization",
        description = "Enter: Token <JWT> or Bearer <JWT>"
)
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping({"/users", "/users/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a user")
    public UserEnvelope register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        return response(users.register(request.user()));
    }

    @PostMapping({"/users/login", "/users/login/"})
    @Operation(summary = "Log in")
    public UserEnvelope login(
            @Valid @RequestBody LoginRequest request
    ) {
        return response(users.login(request.user()));
    }


    private static UserEnvelope response(AuthenticatedUser user) {
        String role = switch (user.role()) {
            case USER -> "User";
            case MODERATOR -> "Moderator";
            case ADMIN -> "Admin";
        };

        return new UserEnvelope(new UserResponse(
                user.username(),
                user.email(),
                user.token(),
                user.bio(),
                user.imageUrl(),
                role
        ));
    }

    @GetMapping({"/user", "/user/"})
    @Operation(summary = "Get current user")
    @SecurityRequirement(name = "tokenAuth")
    public UserEnvelope currentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        CurrentUser user = users.getCurrentUser(UUID.fromString(Objects.requireNonNull(jwt.getSubject())));
        String role = switch (user.role()) {
            case USER -> "User";
            case MODERATOR -> "Moderator";
            case ADMIN -> "Admin";
        };

        return new UserEnvelope(new UserResponse(
                user.username(),
                user.email(),
                jwt.getTokenValue(),
                user.bio(),
                user.imageUrl(),
                role
        ));
    }

    public record RegistrationRequest(@NotNull @Valid RegisterUserCommand user) {

    }

    public record LoginRequest(
            @NotNull @Valid LoginUserCommand user
    ) {
    }

    public record UserEnvelope(UserResponse user) {
    }

    public record UserResponse(
            String username,
            String email,
            String token,
            String bio,
            String image,
            String role
    ) {
    }


}
