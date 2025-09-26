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

    public Map<String, List<ScientificRecord>> detectDuplicates(List<ScientificRecord> records) {
        log.info("Iniciando detección de duplicados en {} registros", records.size());

        Map<String, List<ScientificRecord>> duplicateGroups = new HashMap<>();
        Set<ScientificRecord> processed = new HashSet<>();

        for (ScientificRecord record : records) {
            if (processed.contains(record)) continue;

            List<ScientificRecord> duplicates = findDuplicatesFor(record, records, processed);

            if (duplicates.size() > 1) {
                String groupKey = "group_" + duplicateGroups.size();
                duplicateGroups.put(groupKey, duplicates);
                processed.addAll(duplicates);
            } else {
                processed.add(record);
            }
        }

        log.info("Encontrados {} grupos de duplicados", duplicateGroups.size());
        return duplicateGroups;
    }

    private List<ScientificRecord> findDuplicatesFor(ScientificRecord target,
                                                     List<ScientificRecord> allRecords,
                                                     Set<ScientificRecord> processed) {

        return allRecords.stream()
                .filter(record -> !processed.contains(record))
                .filter(record -> areSimilar(target, record))
                .collect(Collectors.toList());
    }

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
        return score;
    }
}