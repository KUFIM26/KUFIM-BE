package com.example.kufim.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI kufimOpenApi() {
        return new OpenAPI()
                .info(new Info().title("KUFIM API").version("v1")
                        .description("KUFIM API 명세서 v0.4 기반. 관리자 API는 KUFIMSESSION 쿠키와 X-XSRF-TOKEN 헤더를 사용합니다. 개인 API는 X-Client-Token 헤더를 사용합니다."))
                .components(new Components().addSecuritySchemes("cookieAuth",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("KUFIMSESSION"))
                        .addSecuritySchemes("clientToken",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Client-Token")));
    }

    @Bean
    GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("00-all").pathsToMatch("/api/v1/**").build();
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder().group("01-public")
                .pathsToMatch("/api/v1/**").pathsToExclude("/api/v1/admin/**").build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("02-admin").pathsToMatch("/api/v1/admin/**").build();
    }
}
