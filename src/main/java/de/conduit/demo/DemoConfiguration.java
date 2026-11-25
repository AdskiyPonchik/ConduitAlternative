package de.conduit.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conduit.demo.enabled", havingValue = "true")
public class DemoConfiguration {
    @Bean
    ApplicationRunner initializeDemo(DemoDataService data, ConfigurableApplicationContext context,
                                     @Value("${conduit.demo.reset:false}") boolean reset) {
        return arguments -> {
            if (reset && context instanceof WebServerApplicationContext) {
                throw new IllegalStateException("Demo reset must run with --spring.main.web-application-type=none while the server is stopped");
            }
            data.initialize(reset);
            if (reset) context.close();
        };
    }
}
