package de.conduit.identity.internal.application;

import de.conduit.identity.internal.domain.UserConstraints;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.Locale;

public record LoginUserCommand(
        @NotBlank
        @Email
        @Size(max = UserConstraints.EMAIL_MAX_LENGTH)
        String email,

        @NotBlank
        @CodePointLength(max = 128)
        String password
) {

    public LoginUserCommand {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginUserCommand[email=" + email
                + ", password=<redacted>]";
    }
}
