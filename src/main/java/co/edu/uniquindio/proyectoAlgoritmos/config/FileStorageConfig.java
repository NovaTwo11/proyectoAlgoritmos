package co.edu.uniquindio.proyectoAlgoritmos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.file-storage")
@Data
public class FileStorageConfig {
    private String inputPath = "src/main/resources/data/input";
    private String outputPath = "src/main/resources/data/output";
    private long maxFileSize = 10485760; // 10MB
    private String[] allowedExtensions = {"csv", "json", "txt"};
}