package com.security.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO simplificado para listados paginados de Clientes (is_customer = true).
 * Contiene solo información esencial para mejorar la performance del listado.
 */
public class CustomerListDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private Boolean enabled;
    private Boolean accountNonLocked;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // ==================== Constructors ====================

    public CustomerListDTO() {
    }

    public CustomerListDTO(Long id, String firstName, String lastName, String email,
            String phone, Integer totalOrders, BigDecimal totalSpent,
            Boolean enabled, Boolean accountNonLocked, LocalDateTime createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.createdAt = createdAt;
    }

    // ==================== Getters and Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(Boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
}
