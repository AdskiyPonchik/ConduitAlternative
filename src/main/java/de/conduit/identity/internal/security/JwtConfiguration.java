package de.conduit.identity.internal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${conduit.jwt.secret}") String base64Secret
    ) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret);

        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }

        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");

        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }
}
