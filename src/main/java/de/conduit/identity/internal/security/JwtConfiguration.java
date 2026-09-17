package de.conduit.identity.internal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    @Bean
    public SecretKey jwtSigningKey(
            @Value("${conduit.jwt.secret}") String base64Secret
    ) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret);

        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            Clock clock,
            @Value("${conduit.jwt.issuer}") String issuer,
            @Value("${conduit.jwt.audience}") String audience
    ) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));

        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        timestamps.setAllowEmptyNotBeforeClaim(false);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestamps,
                        new JwtIssuerValidator(issuer),
                        new JwtAudienceValidator(audience),
                        new JwtClaimValidator<String>("sub", JwtConfiguration::isUuid)
                )
        );
        return decoder;
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }

        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

}
