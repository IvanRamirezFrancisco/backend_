package com.security.controller;

import com.security.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
// @CrossOrigin(origins = "*")
public class TestController {

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminEndpoint() {
        return ResponseEntity.ok(new ApiResponse(true,
                "This is an admin endpoint - admin role required", null));
    }
}