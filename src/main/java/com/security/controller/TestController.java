package com.security.controller;

import com.security.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * Health check simple - respaldo para Railway
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "auth-system");
        return ResponseEntity.ok(health);
    }

    @GetMapping("/public")
    public ResponseEntity<?> publicEndpoint() {
        return ResponseEntity.ok(new ApiResponse(true,
                "This is a public endpoint - no authentication required", null));
    }

    @GetMapping("/protected")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> protectedEndpoint() {
        return ResponseEntity.ok(new ApiResponse(true,
                "This is a protected endpoint - authentication required", null));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> adminEndpoint() {
        return ResponseEntity.ok(new ApiResponse(true,
                "This is an admin endpoint - admin role required", null));
    }

    /**
     * Endpoint para probar cabeceras de seguridad HTTP
     */
    @GetMapping("/security-headers")
    public ResponseEntity<?> testSecurityHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("info", "Revisa las cabeceras de respuesta HTTP para verificar seguridad");
        headers.put("check", "X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, HSTS, CSP");
        headers.put("tool", "Usa DevTools -> Network para ver las cabeceras");
        
        return ResponseEntity.ok(new ApiResponse(true, "Security headers test", headers));
    }

    /**
     * Endpoint para probar protección XSS
     */
    @PostMapping("/xss-test")
    public ResponseEntity<?> testXSSProtection(@RequestBody Map<String, String> data) {
        String userInput = data.get("input");
        
        // El InputSanitizer debería limpiar esto automáticamente
        Map<String, String> response = new HashMap<>();
        response.put("originalInput", userInput);
        response.put("info", "Si ves scripts ejecutándose, la protección XSS falló");
        response.put("expectedBehavior", "Los scripts deben ser sanitizados");
        
        return ResponseEntity.ok(new ApiResponse(true, "XSS protection test", response));
    }

    /**
     * Endpoint para probar RBAC (Control de Acceso Basado en Roles)
     */
    @GetMapping("/rbac-user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> testRBACUser() {
        return ResponseEntity.ok(new ApiResponse(true, "Acceso USER concedido", 
            Map.of("role", "USER", "message", "Solo usuarios autenticados pueden ver esto")));
    }

    @GetMapping("/rbac-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testRBACAdmin() {
        return ResponseEntity.ok(new ApiResponse(true, "Acceso ADMIN concedido", 
            Map.of("role", "ADMIN", "message", "Solo administradores pueden ver esto")));
    }

    @GetMapping("/rbac-super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> testRBACSuperAdmin() {
        return ResponseEntity.ok(new ApiResponse(true, "Acceso SUPER_ADMIN concedido", 
            Map.of("role", "SUPER_ADMIN", "message", "Solo super administradores pueden ver esto")));
    }
}