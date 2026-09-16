package de.conduit.identity.internal.application;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    public Clock clock(){
        return Clock.systemUTC();
    }
}
