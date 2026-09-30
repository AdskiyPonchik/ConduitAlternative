package de.conduit.users;

import de.conduit.users.dto.ProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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
    public ProfileEnvelope get(@PathVariable("username") String username) {
        return new ProfileEnvelope(users.getProfile(username));
    }

    public record ProfileEnvelope(ProfileView profile) {
    }
}
