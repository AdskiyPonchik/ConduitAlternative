package de.conduit.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer{

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    public JwtTokenIssuer(
            JwtEncoder encoder,
            Clock clock,
            @Value("${conduit.jwt.issuer}") String issuer,
            @Value("${conduit.jwt.audience}") String audience,
            @Value("${conduit.jwt.ttl-seconds}") long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT lifetime must be positive"
            );
        }

        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String issue(UUID userId) {
        Instant now = Instant.now(clock);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
