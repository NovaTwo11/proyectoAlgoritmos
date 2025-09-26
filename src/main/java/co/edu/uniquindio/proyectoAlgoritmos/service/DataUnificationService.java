package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.dto.UnificationStatsDto;
import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ProcessingStatus;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataUnificationService {

    private final DataDownloaderService downloaderService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final FileProcessingService fileProcessingService;
    private final CsvUtils csvUtils;

    @Async
    public CompletableFuture<ProcessingResultDto> processAndUnifyData(String searchQuery) {
        String processId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Iniciando proceso de unificación [{}] con query: {}", processId, searchQuery);

        try {
            // 1. Descargar datos de múltiples fuentes
            List<ScientificRecord> allRecords = downloadFromAllSources(searchQuery);

            // 2. Detectar duplicados
            Map<String, List<ScientificRecord>> duplicateGroups =
                    duplicateDetectionService.detectDuplicates(allRecords);

            // 3. Obtener registros únicos
            List<ScientificRecord> uniqueRecords =
                    duplicateDetectionService.getUniqueRecords(allRecords);

            // 4. Generar archivos de salida
            String unifiedFilePath = fileProcessingService.saveUnifiedRecords(uniqueRecords, processId);
            String duplicatesFilePath = fileProcessingService.saveDuplicateRecords(duplicateGroups, processId);

            // 5. Generar estadísticas
            UnificationStatsDto stats = generateStats(allRecords, uniqueRecords, duplicateGroups);

            LocalDateTime endTime = LocalDateTime.now();

            ProcessingResultDto result = ProcessingResultDto.builder()
                    .processId(processId)
                    .status(ProcessingStatus.COMPLETED)
                    .message("Proceso completado exitosamente")
                    .stats(stats)
                    .startTime(startTime)
                    .endTime(endTime)
                    .unifiedFilePath(unifiedFilePath)
                    .duplicatesFilePath(duplicatesFilePath)
                    .build();

            log.info("Proceso [{}] completado. Únicos: {}, Duplicados: {}",
                    processId, uniqueRecords.size(), stats.getDuplicatesFound());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error en proceso [{}]: {}", processId, e.getMessage(), e);

            ProcessingResultDto errorResult = ProcessingResultDto.builder()
                    .processId(processId)
                    .status(ProcessingStatus.FAILED)
                    .message("Error: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();

            return CompletableFuture.completedFuture(errorResult);
        }
    }

    private List<ScientificRecord> downloadFromAllSources(String searchQuery) {
        List<ScientificRecord> allRecords = new ArrayList<>();

        // Descargar desde ACM
        try {
            List<ScientificRecord> acmRecords = downloaderService.downloadFromSource(DataSource.ACM, searchQuery);
            allRecords.addAll(acmRecords);
        } catch (Exception e) {
            log.warn("Error descargando desde ACM: {}", e.getMessage());
        }

        // Descargar desde SAGE
        try {
            List<ScientificRecord> sageRecords = downloaderService.downloadFromSource(DataSource.SAGE, searchQuery);
            allRecords.addAll(sageRecords);
        } catch (Exception e) {
            log.warn("Error descargando desde SAGE: {}", e.getMessage());
        }

        log.info("Total de registros descargados: {}", allRecords.size());
        return allRecords;
    }

    private UnificationStatsDto generateStats(List<ScientificRecord> allRecords,
                                              List<ScientificRecord> uniqueRecords,
                                              Map<String, List<ScientificRecord>> duplicateGroups) {

        int totalDuplicates = duplicateGroups.values().stream()
                .mapToInt(List::size)
                .sum();

        long recordsFromACM = allRecords.stream()
                .filter(r -> r.getSource() == DataSource.ACM)
                .count();

        long recordsFromSAGE = allRecords.stream()
                .filter(r -> r.getSource() == DataSource.SAGE)
                .count();

        double duplicatePercentage = allRecords.isEmpty() ? 0.0 :
                (double) totalDuplicates / allRecords.size() * 100;

        return UnificationStatsDto.builder()
                .totalRecordsProcessed(allRecords.size())
                .uniqueRecords(uniqueRecords.size())
                .duplicatesFound(totalDuplicates)
                .recordsFromSource1((int) recordsFromACM)
                .recordsFromSource2((int) recordsFromSAGE)
                .duplicatePercentage(duplicatePercentage)
                .build();
    }
}