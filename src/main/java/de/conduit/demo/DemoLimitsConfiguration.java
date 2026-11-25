package de.conduit.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"conduit.demo.enabled", "conduit.demo.limits.enabled"}, havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DemoLimitsConfiguration {
    @Bean
    DemoRequestFilter demoRequestFilter(Clock clock,
            @Value("${conduit.demo.limits.max-body-bytes:524288}") int body,
            @Value("${conduit.demo.limits.login-per-minute:10}") int login,
            @Value("${conduit.demo.limits.registrations-per-hour:5}") int registrations,
            @Value("${conduit.demo.limits.writes-per-minute:30}") int writes) {
        return new DemoRequestFilter(clock, body, login, registrations, writes);
    }

    @Bean
    FilterRegistrationBean<DemoRequestFilter> disableContainerRegistration(DemoRequestFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
