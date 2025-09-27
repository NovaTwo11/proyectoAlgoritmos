package co.edu.uniquindio.proyectoAlgoritmos.util;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilidad para unificar datasets de diferentes fuentes eliminando duplicados
 */
public class DatasetMerger {

    /**
     * Unifica múltiples listas de registros eliminando duplicados
     * @param datasets Lista de datasets a unificar
     * @return Resultado de la unificación
     */
    public static MergeResult mergeDatasets(List<List<ScientificRecord>> datasets) {
        Map<String, ScientificRecord> uniqueRecords = new HashMap<>();
        List<ScientificRecord> duplicates = new ArrayList<>();
        Map<String, Integer> sourceStats = new HashMap<>();

        for (List<ScientificRecord> dataset : datasets) {
            for (ScientificRecord record : dataset) {
                String hash = record.generateUniqueHash();
                String source = record.getSource();

                // Estadísticas por fuente
                sourceStats.put(source, sourceStats.getOrDefault(source, 0) + 1);

                if (uniqueRecords.containsKey(hash)) {
                    // Es un duplicado
                    ScientificRecord existing = uniqueRecords.get(hash);
                    ScientificRecord merged = mergeRecords(existing, record);
                    uniqueRecords.put(hash, merged);
                    duplicates.add(record);
                } else {
                    // Es único
                    uniqueRecords.put(hash, record);
                }
            }
        }

        List<ScientificRecord> unified = new ArrayList<>(uniqueRecords.values());

        return new MergeResult(unified, duplicates, sourceStats);
    }

    /**
     * Combina información de dos registros duplicados
     */
    private static ScientificRecord mergeRecords(ScientificRecord existing, ScientificRecord duplicate) {
        // Mantener el registro existente como base
        ScientificRecord merged = existing;

        // Completar campos faltantes con información del duplicado
        if (merged.getAbstractText() == null || merged.getAbstractText().isEmpty()) {
            merged.setAbstractText(duplicate.getAbstractText());
        }

        if (merged.getKeywords().isEmpty() && !duplicate.getKeywords().isEmpty()) {
            merged.setKeywords(duplicate.getKeywords());
        }

        if (merged.getDoi() == null || merged.getDoi().isEmpty()) {
            merged.setDoi(duplicate.getDoi());
        }

        if (merged.getCitationCount() == 0 && duplicate.getCitationCount() > 0) {
            merged.setCitationCount(duplicate.getCitationCount());
        }

        // Combinar fuentes
        String sources = merged.getSource();
        if (!sources.contains(duplicate.getSource())) {
            merged.setSource(sources + ", " + duplicate.getSource());
        }

        // Combinar campos adicionales
        for (Map.Entry<String, String> entry : duplicate.getAdditionalFields().entrySet()) {
            if (!merged.getAdditionalFields().containsKey(entry.getKey())) {
                merged.addAdditionalField(entry.getKey(), entry.getValue());
            }
        }

        return merged;
    }

    /**
     * Clase para encapsular el resultado de la unificación
     */
    public static class MergeResult {
        private final List<ScientificRecord> unifiedRecords;
        private final List<ScientificRecord> duplicates;
        private final Map<String, Integer> sourceStatistics;

        public MergeResult(List<ScientificRecord> unifiedRecords, 
                          List<ScientificRecord> duplicates, 
                          Map<String, Integer> sourceStatistics) {
            this.unifiedRecords = unifiedRecords;
            this.duplicates = duplicates;
            this.sourceStatistics = sourceStatistics;
        }

        public List<ScientificRecord> getUnifiedRecords() {
            return unifiedRecords;
        }

        public List<ScientificRecord> getDuplicates() {
            return duplicates;
        }

        public Map<String, Integer> getSourceStatistics() {
            return sourceStatistics;
        }

        public void printStatistics() {
            System.out.println("=== Estadísticas de Unificación ===");
            System.out.println("Registros únicos: " + unifiedRecords.size());
            System.out.println("Duplicados encontrados: " + duplicates.size());
            System.out.println("\nRegistros por fuente:");
            sourceStatistics.forEach((source, count) -> 
                System.out.println("  " + source + ": " + count));

            System.out.println("\nDuplicados por fuente:");
            Map<String, Long> duplicatesBySource = duplicates.stream()
                .collect(Collectors.groupingBy(ScientificRecord::getSource, Collectors.counting()));
            duplicatesBySource.forEach((source, count) -> 
                System.out.println("  " + source + ": " + count));
        }
    }
}