package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.StringSimilarityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDetectionService {

    private final StringSimilarityUtils similarityUtils;
    private static final double SIMILARITY_THRESHOLD = 0.85;

    /**
     * Versión optimizada O(n) usando HashMap para indexación
     */
    public Map<String, List<ScientificRecord>> detectDuplicates(List<ScientificRecord> records) {
        log.info("Iniciando detección de duplicados en {} registros", records.size());
        long startTime = System.currentTimeMillis();

        // Paso 1: Indexar por DOI (duplicados exactos)
        Map<String, List<ScientificRecord>> doiGroups = indexByDoi(records);

        // Paso 2: Indexar por título normalizado (duplicados por similitud)
        Map<String, List<ScientificRecord>> titleGroups = indexByNormalizedTitle(records, doiGroups);

        // Paso 3: Combinar grupos y filtrar solo los que tienen duplicados
        Map<String, List<ScientificRecord>> duplicateGroups = new HashMap<>();
        int groupCounter = 0;

        // Agregar grupos de DOI con más de 1 elemento
        for (List<ScientificRecord> group : doiGroups.values()) {
            if (group.size() > 1) {
                duplicateGroups.put("doi_group_" + groupCounter++, group);
            }
        }

        // Agregar grupos de título con más de 1 elemento
        for (List<ScientificRecord> group : titleGroups.values()) {
            if (group.size() > 1) {
                duplicateGroups.put("title_group_" + groupCounter++, group);
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("Encontrados {} grupos de duplicados en {} ms", duplicateGroups.size(), (endTime - startTime));
        return duplicateGroups;
    }

    /**
     * Indexa registros por DOI (duplicados exactos)
     */
    private Map<String, List<ScientificRecord>> indexByDoi(List<ScientificRecord> records) {
        Map<String, List<ScientificRecord>> doiIndex = new HashMap<>();

        for (ScientificRecord record : records) {
            if (record.getDoi() != null && !record.getDoi().trim().isEmpty()) {
                String normalizedDoi = record.getDoi().trim().toLowerCase();
                doiIndex.computeIfAbsent(normalizedDoi, k -> new ArrayList<>()).add(record);
            }
        }

        log.debug("Indexados {} registros únicos por DOI", doiIndex.size());
        return doiIndex;
    }

    /**
     * Indexa registros por título normalizado (excluyendo los ya agrupados por DOI)
     */
    private Map<String, List<ScientificRecord>> indexByNormalizedTitle(List<ScientificRecord> records,
                                                                       Map<String, List<ScientificRecord>> doiGroups) {
        Map<String, List<ScientificRecord>> titleIndex = new HashMap<>();

        // Crear set de registros ya procesados por DOI
        Set<ScientificRecord> processedByDoi = doiGroups.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        for (ScientificRecord record : records) {
            // Solo procesar registros que no fueron agrupados por DOI
            if (!processedByDoi.contains(record)) {
                String normalizedTitle = normalizeTitle(record.getTitle());
                if (!normalizedTitle.isEmpty()) {
                    titleIndex.computeIfAbsent(normalizedTitle, k -> new ArrayList<>()).add(record);
                }
            }
        }

        log.debug("Indexados {} registros únicos por título normalizado", titleIndex.size());
        return titleIndex;
    }

    /**
     * Normaliza título para comparación
     */
    private String normalizeTitle(String title) {
        if (title == null) return "";

        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // Solo letras, números y espacios
                .replaceAll("\\s+", " ")        // Espacios múltiples → uno solo
                .trim();
    }

    /**
     * Versión optimizada de getUniqueRecords
     */
    public List<ScientificRecord> getUniqueRecords(List<ScientificRecord> records) {
        Map<String, List<ScientificRecord>> duplicateGroups = detectDuplicates(records);

        List<ScientificRecord> uniqueRecords = new ArrayList<>();
        Set<ScientificRecord> allDuplicates = new HashSet<>();

        // Agregar un representante de cada grupo de duplicados
        for (List<ScientificRecord> group : duplicateGroups.values()) {
            uniqueRecords.add(selectBestRecord(group));
            allDuplicates.addAll(group);
        }

        // Agregar registros que no tienen duplicados
        records.stream()
                .filter(record -> !allDuplicates.contains(record))
                .forEach(uniqueRecords::add);

        log.info("Registros únicos: {}, Duplicados eliminados: {}",
                uniqueRecords.size(), allDuplicates.size());

        return uniqueRecords;
    }

    private ScientificRecord selectBestRecord(List<ScientificRecord> duplicates) {
        // Selecciona el registro más completo (más campos no nulos)
        return duplicates.stream()
                .max(Comparator.comparingInt(this::calculateCompleteness))
                .orElse(duplicates.get(0));
    }

    private int calculateCompleteness(ScientificRecord record) {
        int score = 0;
        if (record.getTitle() != null && !record.getTitle().trim().isEmpty()) score++;
        if (record.getAuthors() != null && !record.getAuthors().isEmpty()) score++;
        if (record.getAbstractText() != null && !record.getAbstractText().trim().isEmpty()) score++;
        if (record.getKeywords() != null && !record.getKeywords().isEmpty()) score++;
        if (record.getJournal() != null && !record.getJournal().trim().isEmpty()) score++;
        if (record.getDoi() != null && !record.getDoi().trim().isEmpty()) score++;
        if (record.getConference() != null && !record.getConference().trim().isEmpty()) score++;
        if (record.getUrl() != null && !record.getUrl().trim().isEmpty()) score++;
        return score;
    }

    // ========== MÉTODOS LEGACY (por si los necesitas para compatibilidad) ==========

    /**
     * Método legacy mantenido para compatibilidad (pero ya no se usa internamente)
     */
    @Deprecated
    private List<ScientificRecord> findDuplicatesFor(ScientificRecord target,
                                                     List<ScientificRecord> allRecords,
                                                     Set<ScientificRecord> processed) {

        return allRecords.stream()
                .filter(record -> !processed.contains(record))
                .filter(record -> areSimilar(target, record))
                .collect(Collectors.toList());
    }

    /**
     * Método legacy mantenido para compatibilidad
     */
    @Deprecated
    private boolean areSimilar(ScientificRecord record1, ScientificRecord record2) {
        if (record1.equals(record2)) return true;

        // Comparación por título usando similitud de cadenas
        double titleSimilarity = similarityUtils.calculateJaccardSimilarity(
                record1.getTitle(), record2.getTitle()
        );

        if (titleSimilarity >= SIMILARITY_THRESHOLD) return true;

        // Comparación adicional por DOI si existe
        if (record1.getDoi() != null && record2.getDoi() != null) {
            return record1.getDoi().equals(record2.getDoi());
        }

        return false;
    }
}