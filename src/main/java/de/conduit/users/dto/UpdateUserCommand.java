package de.conduit.users.dto;

import de.conduit.users.UserConstraints;
import de.conduit.users.exception.InvalidUserUpdateException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.Locale;

public record UpdateUserCommand(
        @Size(min = 1, max = UserConstraints.USERNAME_MAX_LENGTH) String username,
        @Email @Size(min = 1, max = UserConstraints.EMAIL_MAX_LENGTH) String email,
        @CodePointLength(min = 15, max = 128) String password,
        @Size(max = UserConstraints.BIO_MAX_LENGTH) String bio,
        @Size(max = UserConstraints.IMAGE_URL_MAX_LENGTH) String image
) {
    public UpdateUserCommand {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        bio = bio == null ? null : bio.strip();
        image = image == null ? null : image.strip();
        if (password != null && password.isBlank()) {
            throw new InvalidUserUpdateException("password must not be blank");
        }
    }

    public String toString() {
        return "UpdateUserCommand[username=" + username + ", email=" + email
                + ", password=<redacted>, bio=" + bio + ", image=" + image + "]";
    }
}