package co.edu.uniquindio.proyectoAlgoritmos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProyectoAlgoritmosApplication {
	public static void main(String[] args) {
		SpringApplication.run(ProyectoAlgoritmosApplication.class, args);
	}
}