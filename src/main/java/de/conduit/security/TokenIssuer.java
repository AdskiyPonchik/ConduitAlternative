package de.conduit.security;

import java.util.UUID;

public interface TokenIssuer {
    String issue(UUID userId);
}
