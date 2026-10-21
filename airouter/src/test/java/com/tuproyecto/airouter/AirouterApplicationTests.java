package com.tuproyecto.airouter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo (Smoke Test) que verifica el arranque completo del contexto de Spring Boot,
 * incluyendo la ejecución de las migraciones de Flyway y la validación del esquema JPA sobre H2.
 */
@SpringBootTest
class AirouterApplicationTests {

	/**
	 * Comprueba que el contexto de la aplicación se carga sin errores de configuración ni de esquema.
	 */
	@Test
	void contextLoads() {
	}

}
