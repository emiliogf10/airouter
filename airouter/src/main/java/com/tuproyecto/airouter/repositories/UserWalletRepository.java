package com.tuproyecto.airouter.repositories;

import com.tuproyecto.airouter.models.entities.UserWallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la gestión de persistencia de la entidad {@link UserWallet}.
 *
 * <p>Permite consultar y actualizar el saldo de créditos prepago ({@code Token Credits Wallet})
 * y el historial de recargas asociado a cada identificador de usuario.</p>
 */
@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, UUID> {

    /**
     * Busca la billetera prepago asociada a un identificador de usuario específico.
     *
     * @param userId identificador único del usuario titular de la billetera
     * @return {@link Optional} con la entidad {@link UserWallet} si el usuario dispone de registro de billetera
     */
    Optional<UserWallet> findByUserId(String userId);
}
