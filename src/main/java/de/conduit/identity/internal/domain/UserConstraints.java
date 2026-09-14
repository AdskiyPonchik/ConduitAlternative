package de.conduit.identity.internal.domain;

public final class UserConstraints {
    public static final int USERNAME_MAX_LENGTH = 100;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int PASSWORD_HASH_MAX_LENGTH = 500;
    public static final int BIO_MAX_LENGTH = 300;
    public static final int IMAGE_URL_MAX_LENGTH = 2048;
    public static final int ROLE_MAX_LENGTH = 16;

    private UserConstraints() {
    }
}
