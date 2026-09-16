package de.conduit.identity.internal.application;

import java.util.UUID;

public interface TokenIssuer {
    String issue(UUID userId);
}
