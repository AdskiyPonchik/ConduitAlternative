package de.conduit.users;

import de.conduit.users.dto.ProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static de.conduit.security.JwtPrincipals.actorId;
import static de.conduit.security.JwtPrincipals.viewerId;

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profiles")
public class ProfileController {
    private final UserService users;

    public ProfileController(UserService users) {
        this.users = users;
    }

    @GetMapping({"/{username}", "/{username}/"})
    @Operation(summary = "Get a public profile")
    public ProfileEnvelope get(
            @PathVariable("username") String username,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new ProfileEnvelope(users.getProfile(username, viewerId(jwt)));
    }

    @PostMapping({"/{username}/follow", "/{username}/follow/"})
    @Operation(summary = "Follow a user")
    @SecurityRequirement(name = "tokenAuth")
    public ProfileEnvelope follow(
            @PathVariable("username") String username,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new ProfileEnvelope(users.follow(actorId(jwt), username));
    }

    @DeleteMapping({"/{username}/follow", "/{username}/follow/"})
    @Operation(summary = "Unfollow a user")
    @SecurityRequirement(name = "tokenAuth")
    public ProfileEnvelope unfollow(
            @PathVariable("username") String username,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new ProfileEnvelope(users.unfollow(actorId(jwt), username));
    }

    public record ProfileEnvelope(ProfileView profile) {
    }
}
