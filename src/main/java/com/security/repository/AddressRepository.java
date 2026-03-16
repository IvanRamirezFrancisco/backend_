package com.security.repository;

import com.security.entity.Address;
import com.security.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para gestión de direcciones de usuarios
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * Buscar todas las direcciones activas de un usuario
     */
    List<Address> findByUserAndActiveTrue(User user);

    /**
     * Buscar todas las direcciones de un usuario (activas e inactivas)
     */
    List<Address> findByUser(User user);

    /**
     * Buscar la dirección por defecto de un usuario
     */
    @Query("SELECT a FROM Address a WHERE a.user = :user AND a.isDefault = true AND a.active = true")
    Optional<Address> findDefaultAddressByUser(@Param("user") User user);

    /**
     * Buscar direcciones de un usuario por tipo
     */
    @Query("SELECT a FROM Address a WHERE a.user = :user AND a.addressType = :type AND a.active = true")
    List<Address> findByUserAndType(@Param("user") User user, @Param("type") Address.AddressType type);

    /**
     * Buscar direcciones de un usuario por ciudad
     */
    List<Address> findByUserAndCityIgnoreCaseAndActiveTrue(User user, String city);

    /**
     * Contar direcciones activas de un usuario
     */
    @Query("SELECT COUNT(a) FROM Address a WHERE a.user = :user AND a.active = true")
    long countActiveByUser(@Param("user") User user);

    /**
     * Verificar si un usuario tiene direcciones
     */
    boolean existsByUserAndActiveTrue(User user);

    /**
     * Buscar direcciones por país
     */
    @Query("SELECT a FROM Address a WHERE a.country.id = :countryId AND a.active = true")
    List<Address> findByCountryId(@Param("countryId") Integer countryId);
}
