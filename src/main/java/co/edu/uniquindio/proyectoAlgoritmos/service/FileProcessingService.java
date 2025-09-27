package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.repository.FileRepository;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileProcessingService {

    private final FileRepository fileRepository;
    private final CsvUtils csvUtils;

    public String saveUnifiedRecords(List<ScientificRecord> records, String processId) {
        try {
            fileRepository.ensureDirectoriesExist();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("unified_records_%s_%s.csv", processId, timestamp);
            String filePath = fileRepository.getOutputFilePath(fileName);

            csvUtils.writeRecordsToCsv(records, filePath);

            log.info("Archivo unificado guardado: {} ({} registros)", filePath, records.size());
            return filePath;

        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo unificado", e);
        }
    }

    public String saveDuplicateRecords(Map<String, List<ScientificRecord>> duplicateGroups, String processId) {
        try {
            fileRepository.ensureDirectoriesExist();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("duplicate_records_%s_%s.csv", processId, timestamp);
            String filePath = fileRepository.getOutputFilePath(fileName);

            // Aplanar todos los duplicados en una sola lista
            List<ScientificRecord> allDuplicates = duplicateGroups.values().stream()
                    .flatMap(List::stream)
                    .toList();

            csvUtils.writeRecordsToCsv(allDuplicates, filePath);

            log.info("Archivo de duplicados guardado: {} ({} registros)", filePath, allDuplicates.size());
            return filePath;

        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo de duplicados", e);
        }
    }

    public void cleanupOldFiles(int daysToKeep) {
        // Implementación futura para limpiar archivos antiguos
        log.info("Limpieza de archivos antiguos (implementación pendiente)");
    }

    public String saveUnifiedRecordsFixedName(List<ScientificRecord> records) {
        try {
            fileRepository.ensureDirectoriesExist();
            String path = fileRepository.getOutputFilePath("unified_records.csv");
            csvUtils.writeRecordsToCsv(records, path);
            log.info("Archivo unificado (nombre fijo) guardado: {}", path);
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Error guardando unified_records.csv", e);
        }
    }

    public String saveDuplicateRecordsFixedName(Map<String, List<ScientificRecord>> duplicateGroups) {
        try {
            fileRepository.ensureDirectoriesExist();
            String path = fileRepository.getOutputFilePath("duplicate_records.csv");
            List<ScientificRecord> allDup = duplicateGroups.values().stream().flatMap(List::stream).toList();
            csvUtils.writeRecordsToCsv(allDup, path);
            log.info("Archivo de duplicados (nombre fijo) guardado: {}", path);
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Error guardando duplicate_records.csv", e);
        }
    }
}