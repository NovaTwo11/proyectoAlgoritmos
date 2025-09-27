package co.edu.uniquindio.proyectoAlgoritmos;

import co.edu.uniquindio.proyectoAlgoritmos.service.DataUnificationService;
import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CompletableFuture;

@SpringBootApplication
public class ProjectRunner {

    public static void main(String[] args) throws Exception {
        var context = SpringApplication.run(ProjectRunner.class, args);

        DataUnificationService service = context.getBean(DataUnificationService.class);

        // Lanza un proceso automático de unificación de datasets
        CompletableFuture<ProcessingResultDto> future =
                service.processAndUnifyData("generative ai");

        ProcessingResultDto result = future.get(); // bloquea hasta terminar

        System.out.println("\n=== RESULTADO DEL PROCESO ===");
        System.out.println("Estado: " + result.getStatus());
        System.out.println("Registros únicos en archivo: " + result.getUnifiedFilePath());
        System.out.println("Duplicados en archivo: " + result.getDuplicatesFilePath());
    }
}