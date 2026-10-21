package com.tuproyecto.airouter.repositories;

import com.tuproyecto.airouter.models.entities.RequestLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la gestión de persistencia de la entidad {@link RequestLog}.
 *
 * <p>Proporciona operaciones CRUD estándar y consultas derivadas para recuperar el historial
 * de peticiones de enrutamiento ordenadas cronológicamente.</p>
 */
@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, UUID> {

    /**
     * Recupera todos los registros de peticiones ordenados por fecha de creación
     * de forma descendente (los más recientes primero).
     *
     * @return lista de registros {@link RequestLog} ordenados de más reciente a más antiguo
     */
    List<RequestLog> findAllByOrderByCreatedAtDesc();

    /**
     * Recupera todos los registros de peticiones asociados a un usuario específico,
     * ordenados por fecha de creación de forma descendente.
     *
     * @param userId identificador del usuario cuyas peticiones se desean consultar
     * @return lista de registros {@link RequestLog} del usuario ordenados de más reciente a más antiguo
     */
    List<RequestLog> findByUserIdOrderByCreatedAtDesc(String userId);
}
