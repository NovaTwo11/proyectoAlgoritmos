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
            // 1. Descargar datos de DBLP + OpenAlex
            List<ScientificRecord> allRecords = downloaderService.downloadFromAllSources(searchQuery);

            if (allRecords.isEmpty()) {
                log.warn("No se descargaron registros de ninguna fuente");
                return CompletableFuture.completedFuture(createEmptyResult(processId, startTime));
            }

            // 2. Detectar duplicados
            Map<String, List<ScientificRecord>> duplicateGroups =
                    duplicateDetectionService.detectDuplicates(allRecords);

            // 3. Obtener registros únicos
            List<ScientificRecord> uniqueRecords =
                    duplicateDetectionService.getUniqueRecords(allRecords);

            // 4. Generar archivos de salida con nombres fijos según requisitos
            String unifiedFilePath = saveUnifiedRecords(uniqueRecords);
            String duplicatesFilePath = saveDuplicateRecords(duplicateGroups);

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
                    .unifiedRecords(uniqueRecords)
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

    /**
     * Guarda registros unificados con nombre fijo según requisitos del proyecto
     */
    private String saveUnifiedRecords(List<ScientificRecord> records) {
        try {
            String fileName = "resultados_unificados.csv";
            String filePath = "src/main/resources/data/output/" + fileName;

            csvUtils.writeRecordsToCsv(records, filePath);
            log.info("Archivo unificado guardado: {} ({} registros)", filePath, records.size());

            return filePath;
        } catch (Exception e) {
            throw new RuntimeException("Error guardando archivo unificado", e);
        }
    }

    /**
     * Guarda registros duplicados con nombre fijo según requisitos del proyecto
     */
    private String saveDuplicateRecords(Map<String, List<ScientificRecord>> duplicateGroups) {
        try {
            String fileName = "resultados_duplicados.csv";
            String filePath = "src/main/resources/data/output/" + fileName;

            // Aplanar todos los duplicados en una sola lista
            List<ScientificRecord> allDuplicates = duplicateGroups.values().stream()
                    .flatMap(List::stream)
                    .toList();

            csvUtils.writeRecordsToCsv(allDuplicates, filePath);
            log.info("Archivo de duplicados guardado: {} ({} registros)", filePath, allDuplicates.size());

            return filePath;
        } catch (Exception e) {
            throw new RuntimeException("Error guardando archivo de duplicados", e);
        }
    }

    private ProcessingResultDto createEmptyResult(String processId, LocalDateTime startTime) {
        UnificationStatsDto emptyStats = UnificationStatsDto.builder()
                .totalRecordsProcessed(0)
                .uniqueRecords(0)
                .duplicatesFound(0)
                .recordsFromSource1(0)
                .recordsFromSource2(0)
                .duplicatePercentage(0.0)
                .build();

        return ProcessingResultDto.builder()
                .processId(processId)
                .status(ProcessingStatus.COMPLETED)
                .message("No se encontraron registros para procesar")
                .stats(emptyStats)
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .build();
    }

    private UnificationStatsDto generateStats(List<ScientificRecord> allRecords,
                                              List<ScientificRecord> uniqueRecords,
                                              Map<String, List<ScientificRecord>> duplicateGroups) {

        int totalDuplicates = duplicateGroups.values().stream()
                .mapToInt(List::size)
                .sum();

        long recordsFromDBLP = allRecords.stream()
                .filter(r -> DataSource.DBLP.toString().equals(r.getSource()))
                .count();

        long recordsFromOpenAlex = allRecords.stream()
                .filter(r -> DataSource.OPENALEX.toString().equals(r.getSource()))
                .count();

        double duplicatePercentage = allRecords.isEmpty() ? 0.0 :
                (double) totalDuplicates / allRecords.size() * 100;

        return UnificationStatsDto.builder()
                .totalRecordsProcessed(allRecords.size())
                .uniqueRecords(uniqueRecords.size())
                .duplicatesFound(totalDuplicates)
                .recordsFromSource1((int) recordsFromDBLP)
                .recordsFromSource2((int) recordsFromOpenAlex)
                .duplicatePercentage(duplicatePercentage)
                .build();
    }
}