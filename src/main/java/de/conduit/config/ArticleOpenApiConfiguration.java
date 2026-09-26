package de.conduit.config;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger configuration
@Configuration
public class ArticleOpenApiConfiguration {
    @Bean
    OpenApiCustomizer articleReadOptionalAuthentication() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path : List.of(
                    "/api/articles", "/api/articles/",
                    "/api/articles/{slug}", "/api/articles/{slug}/"
            )) {
                var item = openApi.getPaths().get(path);
                if (item != null && item.getGet() != null) {
                    item.getGet().setSecurity(List.of(
                            new SecurityRequirement(),
                            new SecurityRequirement().addList("tokenAuth")
                    ));
                }
            }
        };
    }
}
