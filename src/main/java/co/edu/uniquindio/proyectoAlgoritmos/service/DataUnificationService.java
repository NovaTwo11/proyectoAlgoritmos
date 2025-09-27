package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.dto.UnificationStatsDto;
import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ProcessingStatus;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import co.edu.uniquindio.proyectoAlgoritmos.util.ValidationUtils;
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
    private final ValidationUtils validationUtils;

    private final co.edu.uniquindio.proyectoAlgoritmos.reader.api.DblpApiReader dblpApiReader;
    private final co.edu.uniquindio.proyectoAlgoritmos.reader.api.ScopusApiReader scopusApiReader;

    @Async
    public CompletableFuture<ProcessingResultDto> processAndUnifyData(String searchQuery) {
        String processId = java.util.UUID.randomUUID().toString();
        var startTime = java.time.LocalDateTime.now();
        String q = validationUtils.sanitizeSearchQuery(searchQuery);

        try {
            // 1) Descargar datos reales (APIs/fallback)
            List<ScientificRecord> allRecords = downloadFromAllSources(q);

            // 2) Duplicados
            Map<String, List<ScientificRecord>> duplicateGroups =
                    duplicateDetectionService.detectDuplicates(allRecords);

            // 3) Únicos
            List<ScientificRecord> uniqueRecords =
                    duplicateDetectionService.getUniqueRecords(allRecords);

            // 4) Guardado (dos archivos fijos + (opcional) versionados)
            String unifiedFixed = fileProcessingService.saveUnifiedRecordsFixedName(uniqueRecords);
            String duplicatesFixed = fileProcessingService.saveDuplicateRecordsFixedName(duplicateGroups);

            // Opcional: mantener además archivos versionados
            // String unifiedVersioned = fileProcessingService.saveUnifiedRecords(uniqueRecords, processId);
            // String duplicatesVersioned = fileProcessingService.saveDuplicateRecords(duplicateGroups, processId);

            // 5) Stats
            UnificationStatsDto stats = generateStats(allRecords, uniqueRecords, duplicateGroups);

            var endTime = java.time.LocalDateTime.now();
            var result = ProcessingResultDto.builder()
                    .processId(processId)
                    .status(ProcessingStatus.COMPLETED)
                    .message("Proceso completado exitosamente")
                    .stats(stats)
                    .startTime(startTime)
                    .endTime(endTime)
                    .unifiedFilePath(unifiedFixed)
                    .duplicatesFilePath(duplicatesFixed)
                    .build();

            log.info("Proceso [{}] completado. Únicos: {}, Duplicados: {}",
                    processId, uniqueRecords.size(), stats.getDuplicatesFound());

            return java.util.concurrent.CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error en proceso [{}]: {}", processId, e.getMessage(), e);
            var errorResult = ProcessingResultDto.builder()
                    .processId(processId)
                    .status(ProcessingStatus.FAILED)
                    .message("Error: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(java.time.LocalDateTime.now())
                    .build();
            return java.util.concurrent.CompletableFuture.completedFuture(errorResult);
        }
    }

    private List<ScientificRecord> downloadFromAllSources(String q) {
        List<ScientificRecord> all = new ArrayList<>();

        // DBLP API (pública)
        try {
            var dblp = dblpApiReader.searchRecords(q);
            log.info("DBLP retornó {} registros", dblp.size());
            all.addAll(dblp);
        } catch (Exception e) {
            log.warn("Fallo DBLP: {}", e.getMessage());
        }

        // Scopus API (si hay key), si no fallback a ScienceDirect CSV
        try {
            var scopus = scopusApiReader.searchRecords(q); // lanza si no hay key
            log.info("Scopus retornó {} registros", scopus.size());
            all.addAll(scopus);
        } catch (IllegalStateException noKey) {
            log.warn("Scopus API Key no configurada. Fallback a ScienceDirect CSV.");
            try {
                var sd = downloaderService.downloadFromSource(DataSource.SCIENCE_DIRECT, q);
                log.info("ScienceDirect CSV retornó {} registros", sd.size());
                all.addAll(sd);
            } catch (Exception e) {
                log.warn("Fallo fallback ScienceDirect CSV: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Fallo Scopus API: {}", e.getMessage());
        }

        log.info("Total registros descargados (todas las fuentes): {}", all.size());
        return all;
    }

    private UnificationStatsDto generateStats(List<ScientificRecord> allRecords,
                                              List<ScientificRecord> uniqueRecords,
                                              Map<String, List<ScientificRecord>> duplicateGroups) {

        int totalDuplicates = duplicateGroups.values().stream()
                .mapToInt(List::size)
                .sum();

        long recordsFromACM = allRecords.stream()
                .filter(r -> r.getSource() == DataSource.ACM.toString())
                .count();

        long recordsFromSAGE = allRecords.stream()
                .filter(r -> r.getSource() == DataSource.SAGE.toString())
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