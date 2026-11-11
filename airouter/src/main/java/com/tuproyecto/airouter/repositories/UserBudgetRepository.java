package com.tuproyecto.airouter.repositories;

import com.tuproyecto.airouter.models.entities.UserBudget;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la gestión de persistencia de la entidad {@link UserBudget}.
 *
 * <p>Permite consultar, almacenar y eliminar los límites presupuestarios (FinOps / Hard Budget Cap)
 * asociados a cada identificador de usuario.</p>
 */
@Repository
public interface UserBudgetRepository extends JpaRepository<UserBudget, UUID> {

    /**
     * Busca la configuración de presupuesto asociada a un identificador de usuario específico.
     *
     * @param userId identificador único del usuario
     * @return {@link Optional} con la entidad {@link UserBudget} si el usuario tiene un presupuesto configurado
     */
    Optional<UserBudget> findByUserId(String userId);

    /**
     * Elimina el registro de presupuesto asociado al identificador de usuario indicado.
     *
     * @param userId identificador único del usuario cuyo límite se desea suprimir
     */
    void deleteByUserId(String userId);
}
