package de.conduit.users;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.Objects;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    protected User() {
    }

    public static User register(
            UUID id,
            String username,
            String email,
            String passwordHash,
            Instant now) {
        User user = new User();
        user.id = Objects.requireNonNull(id, "id is required");
        user.username = requiredText(username, "username", UserConstraints.USERNAME_MAX_LENGTH);
        user.email = requiredText(email, "email", UserConstraints.EMAIL_MAX_LENGTH).toLowerCase(Locale.ROOT);
        user.passwordHash = requiredText(passwordHash, "passwordHash", UserConstraints.PASSWORD_HASH_MAX_LENGTH);
        user.role = UserRole.USER;
        Instant validNow = Objects.requireNonNull(now, "now is required");
        user.createdAt = validNow;
        user.updatedAt = validNow;
        user.bio = "";
        user.imageUrl = "";
        return user;
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = UserConstraints.USERNAME_MAX_LENGTH)
    private String username;

    @Column(nullable = false, length = UserConstraints.EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "password_hash",
            nullable = false,
            length = UserConstraints.PASSWORD_HASH_MAX_LENGTH)
    private String passwordHash;

    @Column(nullable = false, length = UserConstraints.BIO_MAX_LENGTH)
    private String bio;

    @Column(name = "image_url",
            nullable = false,
            length = UserConstraints.IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = UserConstraints.ROLE_MAX_LENGTH)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    private static String requiredText(String value, String fieldName, int maxLength) {

        String normalized = optionalText(value, fieldName, maxLength);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(
            String value,
            String fieldName,
            int maxLength
    ) {
        String normalized = value == null ? "" : value.strip();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must be at most "
                            + maxLength + " characters"
            );
        }
        return normalized;
    }

    public void updateProfile(String username,
                              String bio,
                              String imageUrl,
                              Instant now) {
        String validUsername = requiredText(username, "username", UserConstraints.USERNAME_MAX_LENGTH);
        String validBio = optionalText(bio, "bio", UserConstraints.BIO_MAX_LENGTH);
        String validImageUrl = optionalText(imageUrl, "imageUrl", UserConstraints.IMAGE_URL_MAX_LENGTH);
        Instant validNow = Objects.requireNonNull(now, "now is required");
        this.username = validUsername;
        this.bio = validBio;
        this.imageUrl = validImageUrl;
        this.updatedAt = validNow;
    }


    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
