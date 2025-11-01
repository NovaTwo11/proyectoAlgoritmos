package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JaccardSimilarity {

    /**
     * API estándar para el frontend: recibe un mapa id -> texto (abstract)
     * y retorna resultados de similitud Jaccard por pares.
     */
    public AlgorithmRunResult jaccardDistance(Map<String, String> idToText) {
        long t0 = System.currentTimeMillis();
        if (idToText == null || idToText.isEmpty()) {
            return AlgorithmRunResult.builder()
                    .algorithm("Jaccard")
                    .totalTimeMs(0)
                    .totalComparisons(0)
                    .results(Collections.emptyList())
                    .build();
        }
        // materializar entradas y normalizar nulos a ""
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.toList());

        // tokenizar una vez por entrada
        List<Set<String>> tokens = new ArrayList<>(entries.size());
        for (Map.Entry<String,String> e : entries) tokens.add(tokenize(e.getValue()));

        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                String idA = entries.get(i).getKey();
                String idB = entries.get(j).getKey();
                Set<String> a = tokens.get(i);
                Set<String> b = tokens.get(j);
                long t1 = System.nanoTime();
                double s = jaccard(a, b);
                long t2 = System.nanoTime();
                results.add(AlgorithmPairResult.builder()
                        .idA(idA)
                        .idB(idB)
                        .distance((int)Math.round((1.0 - s) * 1000.0)) // jaccard distance * 1000
                        .score(s)
                        .similarityPercent(Math.round(s * 10000.0) / 100.0)
                        .timeMs(Math.max(0L, (t2 - t1) / 1_000_000L))
                        .build());
            }
        }
        results.sort(Comparator
                .comparingDouble(AlgorithmPairResult::getScore).reversed()
                .thenComparing(AlgorithmPairResult::getIdA)
                .thenComparing(AlgorithmPairResult::getIdB));

        long total = System.currentTimeMillis() - t0;
        return AlgorithmRunResult.builder()
                .algorithm("Jaccard")
                .totalTimeMs(total)
                .totalComparisons(results.size())
                .results(results)
                .build();
    }

    // --- utilidades ---

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        // normalización simple: lower, separar por no alfanumérico, filtrar tokens cortos
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> set = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 2) set.add(p); // descartar tokens de 1 char
        }
        return set;
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        // iterar sobre el más pequeño
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = a.size() > b.size() ? a : b;
        for (String t : small) if (large.contains(t)) intersection++;
        int union = a.size() + b.size() - intersection;
        if (union == 0) return 1.0;
        return intersection / (double) union;
    }
}

