package de.conduit.users;

import de.conduit.users.dto.AuthenticatedUser;
import de.conduit.users.dto.CurrentUser;
import de.conduit.users.dto.LoginUserCommand;
import de.conduit.users.dto.RegisterUserCommand;
import de.conduit.users.dto.UpdateUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static de.conduit.security.JwtPrincipals.actorId;

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
    public UserEnvelope register(@Valid @RequestBody RegistrationRequest request) {
        return response(users.register(request.user()));
    }

    @PostMapping({"/users/login", "/users/login/"})
    @Operation(summary = "Log in")
    public UserEnvelope login(@Valid @RequestBody LoginRequest request) {
        return response(users.login(request.user()));
    }

    @GetMapping({"/user", "/user/"})
    @Operation(summary = "Get current user")
    @SecurityRequirement(name = "tokenAuth")
    public UserEnvelope currentUser(@AuthenticationPrincipal Jwt jwt) {
        return response(users.getCurrentUser(actorId(jwt)), jwt.getTokenValue());
    }

    @PutMapping({"/user", "/user/"})
    @Operation(summary = "Update current user")
    @SecurityRequirement(name = "tokenAuth")
    public UserEnvelope update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return response(users.updateCurrentUser(actorId(jwt), request.user()), jwt.getTokenValue());
    }

    /**
     * Login and registration return a newly issued token.
     */
    private static UserEnvelope response(AuthenticatedUser user) {
        return new UserEnvelope(new UserResponse(
                user.username(), user.email(), user.token(), user.bio(), user.imageUrl(), roleName(user.role())
        ));
    }

    /**
     * Current-account routes echo the token the request was authenticated with.
     */
    private static UserEnvelope response(CurrentUser user, String token) {
        return new UserEnvelope(new UserResponse(
                user.username(), user.email(), token, user.bio(), user.imageUrl(), roleName(user.role())
        ));
    }

    private static String roleName(UserRole role) {
        return switch (role) {
            case USER -> "User";
            case MODERATOR -> "Moderator";
            case ADMIN -> "Admin";
        };
    }

    public record RegistrationRequest(@NotNull @Valid RegisterUserCommand user) {
    }

    public record LoginRequest(@NotNull @Valid LoginUserCommand user) {
    }

    public record UpdateUserRequest(@NotNull @Valid UpdateUserCommand user) {
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
