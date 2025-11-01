package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TFIDFCosineSimilarity {

    /**
     * API para frontend: recibe id->texto (abstract) y retorna resultados por pares usando TF-IDF + coseno.
     */
    public AlgorithmRunResult tfidfCosineDistance(Map<String, String> idToText) {
        long t0 = System.currentTimeMillis();
        if (idToText == null || idToText.isEmpty()) {
            return AlgorithmRunResult.builder()
                    .algorithm("TFIDF_COSINE")
                    .totalTimeMs(0)
                    .totalComparisons(0)
                    .results(Collections.emptyList())
                    .build();
        }
        // materializar entradas y normalizar nulos a ""
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.toList());

        // 1) TF por documento y vocab
        int N = entries.size();
        List<Map<String, Integer>> tfs = new ArrayList<>(N);
        Map<String, Integer> df = new HashMap<>();
        for (Map.Entry<String,String> e : entries) {
            Map<String, Integer> tf = termFreq(e.getValue());
            tfs.add(tf);
            // actualizar DF
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum);
        }

        // 2) IDF con suavizado
        Map<String, Double> idf = new HashMap<>(df.size());
        for (Map.Entry<String,Integer> dfe : df.entrySet()) {
            int docFreq = dfe.getValue();
            double val = Math.log((N + 1.0) / (docFreq + 1.0)) + 1.0; // idf suavizado
            idf.put(dfe.getKey(), val);
        }

        // 3) Vectores TF-IDF normalizados (l2)
        List<Map<String, Double>> vectors = new ArrayList<>(N);
        for (Map<String, Integer> tf : tfs) {
            Map<String, Double> vec = new HashMap<>();
            double sumSquares = 0.0;
            for (Map.Entry<String,Integer> te : tf.entrySet()) {
                Double idfVal = idf.get(te.getKey());
                if (idfVal == null) continue;
                // TF: log-normalized (1 + log(tf)) ayuda a atenuar términos frecuentes
                double tfWeight = 1.0 + Math.log(te.getValue());
                double w = tfWeight * idfVal;
                vec.put(te.getKey(), w);
                sumSquares += w * w;
            }
            double norm = sumSquares > 0 ? Math.sqrt(sumSquares) : 1.0;
            if (norm != 0) {
                double inv = 1.0 / norm;
                for (Map.Entry<String, Double> ve : vec.entrySet()) {
                    ve.setValue(ve.getValue() * inv);
                }
            }
            vectors.add(vec);
        }

        // 4) Comparar pares (i<j) con producto punto (coseno, al estar normalizados)
        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                String idA = entries.get(i).getKey();
                String idB = entries.get(j).getKey();
                Map<String, Double> a = vectors.get(i);
                Map<String, Double> b = vectors.get(j);
                long t1 = System.nanoTime();
                double s = dot(a, b);
                long t2 = System.nanoTime();
                results.add(AlgorithmPairResult.builder()
                        .idA(idA)
                        .idB(idB)
                        .distance((int)Math.round((1.0 - s) * 1000.0))
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
                .algorithm("TFIDF_COSINE")
                .totalTimeMs(total)
                .totalComparisons(results.size())
                .results(results)
                .build();
    }

    // --- utilidades ---

    private Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String p : parts) {
            if (p.length() >= 2) tf.merge(p, 1, Integer::sum);
        }
        return tf;
    }

    private double dot(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return (a.isEmpty() && b.isEmpty()) ? 1.0 : 0.0;
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = a.size() > b.size() ? a : b;
        double sum = 0.0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) sum += e.getValue() * w;
        }
        // por construcción, ambos están normalizados (coseno en [0,1])
        if (sum < 0) sum = 0.0; // seguridad numérica
        if (sum > 1) sum = 1.0;
        return sum;
    }
}

