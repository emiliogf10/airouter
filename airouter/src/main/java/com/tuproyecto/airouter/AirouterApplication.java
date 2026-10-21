package com.tuproyecto.airouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de arranque de la aplicación Spring Boot <b>AI Cost Router</b>.
 *
 * <p>Inicializa el contexto de Spring, la autoconfiguración de migraciones Flyway,
 * la validación estricta de entidades JPA y los controladores REST de la pasarela de IA.</p>
 */
@SpringBootApplication
public class AirouterApplication {

	/**
	 * Punto de entrada principal para la ejecución de la aplicación.
	 *
	 * @param args argumentos de línea de comandos pasados al iniciar el proceso
	 */
	public static void main(String[] args) {
		SpringApplication.run(AirouterApplication.class, args);
	}

}
