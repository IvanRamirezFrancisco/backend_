package com.security.config;

import com.security.security.CurrentUserArgumentResolver;
import com.security.interceptor.SecurityInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CurrentUserArgumentResolver currentUserArgumentResolver;

    @Autowired
    private SecurityInterceptor securityInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/test/public", "/actuator/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:4200",
                        "http://localhost:8080",
                        "https://fronlogin-production.up.railway.app",
                        "https://frontendapp-production.up.railway.app")
                .allowedOriginPatterns(
                        "https://*.railway.app",
                        "https://*.up.railway.app")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With",
                        "Cache-Control")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}