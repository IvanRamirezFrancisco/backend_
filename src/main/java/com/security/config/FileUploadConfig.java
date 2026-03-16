package com.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuración para servir archivos estáticos (imágenes subidas)
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${upload.path:uploads/products}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Convertir a path absoluto
        Path absolutePath = Paths.get(uploadPath).toAbsolutePath();

        // Mapear /uploads/products/** a la carpeta física uploads/products/
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations("file:" + absolutePath.toString() + "/")
                .setCachePeriod(3600);

        System.out.println("📁 Serving static files from: " + absolutePath.toString());
        System.out.println("🔗 URL pattern: /uploads/products/** -> file:" + absolutePath.toString());
    }

    /**
     * Bean para manejar multipart requests
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
