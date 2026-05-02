package com.suannai.netdisk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.CharacterEncodingFilter;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, CorsProperties.class})
public class NetDiskConfiguration {

    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(CorsProperties corsProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (!corsProperties.hasAllowedSources()) {
                    return;
                }

                var registration = registry.addMapping("/api/**")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                if (!corsProperties.getAllowedOrigins().isEmpty()) {
                    registration.allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
                }
                if (!corsProperties.getAllowedOriginPatterns().isEmpty()) {
                    registration.allowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
                }
            }
        };
    }
}
