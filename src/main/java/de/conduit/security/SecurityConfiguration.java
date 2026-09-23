package de.conduit.security;


import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.regex.Pattern;


@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "^(?:Bearer|Token) "
                    + "([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)$",
            Pattern.CASE_INSENSITIVE
    );

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return request -> {
            var headers = Collections.list(
                    request.getHeaders(HttpHeaders.AUTHORIZATION)
            );

            if (headers.isEmpty()) {
                return null;
            }

            if (headers.size() != 1) {
                throw invalidAuthorizationHeader();
            }

            var matcher = AUTHORIZATION.matcher(headers.getFirst());

            if (!matcher.matches()) {
                throw invalidAuthorizationHeader();
            }

            return matcher.group(1);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users",
                                "/api/users/",
                                "/api/users/login",
                                "/api/users/login/"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tags",
                                "/api/tags/"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/articles/feed",
                                "/api/articles/feed/"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/articles/{slug}",
                                "/api/articles/{slug}/"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(Customizer.withDefaults())
                )
                .build();
    }
    private static OAuth2AuthenticationException invalidAuthorizationHeader(){
        return new OAuth2AuthenticationException(
                new OAuth2Error(
                        "invalid_token",
                        "Invalid Authorization header",
                        null
                )
        );
    }
}
