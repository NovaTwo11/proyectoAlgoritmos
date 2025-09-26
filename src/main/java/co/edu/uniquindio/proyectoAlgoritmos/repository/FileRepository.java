package co.edu.uniquindio.proyectoAlgoritmos.repository;

import co.edu.uniquindio.proyectoAlgoritmos.config.FileStorageConfig;
import co.edu.uniquindio.proyectoAlgoritmos.exception.DataProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FileRepository {

    private final FileStorageConfig fileStorageConfig;

    public void ensureDirectoriesExist() {
        try {
            Path inputPath = Paths.get(fileStorageConfig.getInputPath());
            Path outputPath = Paths.get(fileStorageConfig.getOutputPath());

            Files.createDirectories(inputPath);
            Files.createDirectories(outputPath);

            log.info("Directorios creados/verificados: input={}, output={}",
                    inputPath, outputPath);

        } catch (IOException e) {
            throw new DataProcessingException("Error creando directorios", e);
        }
    }

    public List<File> getInputFiles() {
        try {
            Path inputPath = Paths.get(fileStorageConfig.getInputPath());

            try (Stream<Path> paths = Files.walk(inputPath)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(this::isAllowedFileType)
                        .map(Path::toFile)
                        .toList();
            }

        } catch (IOException e) {
            throw new DataProcessingException("Error listando archivos de entrada", e);
        }
    }

    public String getOutputFilePath(String fileName) {
        return Paths.get(fileStorageConfig.getOutputPath(), fileName).toString();
    }

    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    public long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("No se pudo obtener el tamaño del archivo: {}", filePath);
            return 0;
        }
    }

    private boolean isAllowedFileType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        for (String extension : fileStorageConfig.getAllowedExtensions()) {
            if (fileName.endsWith("." + extension)) {
                return true;
            }
        }

        return false;
    }
}