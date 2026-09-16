package de.conduit.identity.internal.application;


import de.conduit.identity.internal.domain.UserConstraints;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.Locale;


public record RegisterUserCommand(
        @NotBlank
        @Size(max = UserConstraints.USERNAME_MAX_LENGTH)
        String username,

        @NotBlank
        @Email
        @Size(max = UserConstraints.EMAIL_MAX_LENGTH)
        String email,

        @NotBlank
        @CodePointLength(min = 15, max = 128)
        String password
) {
    public RegisterUserCommand{
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString(){
        return "RegisterUserCommand[username=" + username
                + ", email=" + email
                + ", password=<redacted>]";
    }
}
