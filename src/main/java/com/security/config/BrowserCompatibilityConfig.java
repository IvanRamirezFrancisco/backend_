package com.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Configuración de headers de seguridad compatible con navegadores
 */
@Configuration
public class BrowserCompatibilityConfig implements WebMvcConfigurer {

    /**
     * Filtro para headers de seguridad compatibles con navegadores
     */
    @WebFilter("/*")
    public static class BrowserCompatibilityFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // Inicialización si es necesaria
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletResponse httpResponse = (HttpServletResponse) response;
            jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;

            // CSP más permisivo para desarrollo y producción
            httpResponse.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdnjs.cloudflare.com https://fonts.googleapis.com; "
                            +
                            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                            "img-src 'self' data: https:; " +
                            "font-src 'self' https://fonts.gstatic.com; " +
                            "connect-src 'self' http://localhost:* ws://localhost:* https:; " +
                            "frame-ancestors 'self'");

            // Headers adicionales para compatibilidad con navegadores
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Cache headers para mejor performance en recursos estáticos
            String servletPath = httpRequest.getServletPath();
            if (servletPath.contains("/assets/") ||
                    servletPath.endsWith(".css") ||
                    servletPath.endsWith(".js") ||
                    servletPath.endsWith(".png") ||
                    servletPath.endsWith(".jpg") ||
                    servletPath.endsWith(".svg")) {
                httpResponse.setHeader("Cache-Control", "public, max-age=31536000");
            }

            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            // Limpieza si es necesaria
        }
    }
}