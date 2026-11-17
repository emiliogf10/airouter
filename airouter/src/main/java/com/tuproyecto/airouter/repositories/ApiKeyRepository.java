package com.tuproyecto.airouter.repositories;

import com.tuproyecto.airouter.models.entities.ApiKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la gestión de persistencia de la entidad {@link ApiKey}.
 *
 * <p>Permite listar las claves emitidas para un usuario y verificar la autenticidad de una clave
 * entrante buscando exclusivamente por su resumen criptográfico SHA-256 ({@code keyHash}).</p>
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Recupera todas las claves API asociadas a un usuario ordenadas por fecha de creación
     * de forma descendente (las más recientes primero).
     *
     * @param userId identificador del usuario propietario de las claves
     * @return lista de entidades {@link ApiKey} del usuario ordenadas cronológicamente
     */
    List<ApiKey> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Busca una clave API a partir de su hash criptográfico SHA-256 hexadecimal de 64 caracteres.
     *
     * @param keyHash resumen SHA-256 en minúsculas de la clave API completa
     * @return {@link Optional} con la entidad {@link ApiKey} si existe coincidencia
     */
    Optional<ApiKey> findByKeyHash(String keyHash);
}
