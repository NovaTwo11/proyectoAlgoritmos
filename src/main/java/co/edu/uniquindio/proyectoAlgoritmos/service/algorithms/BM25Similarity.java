package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Similaridad BM25 simétrica documento-documento.
 * - Entrada: id -> texto (abstract)
 * - Salida: AlgorithmRunResult (score normalizado [0..1] por par)
 *
 * Nota: BM25 es un modelo consulta-documento; para doc-doc usamos promedio: (BM25(A->B)+BM25(B->A))/2
 * y normalizamos en [0,1] con min-max dentro del mismo run.
 */
@Slf4j
@Service
public class BM25Similarity {

    private static final double K1 = 1.5;   // saturación TF doc
    private static final double B = 0.75;   // normalización longitud

    public AlgorithmRunResult bm25Distance(Map<String, String> idToText) {
        long t0 = System.currentTimeMillis();
        if (idToText == null || idToText.isEmpty()) {
            return AlgorithmRunResult.builder()
                    .algorithm("BM25")
                    .totalTimeMs(0)
                    .totalComparisons(0)
                    .results(Collections.emptyList())
                    .build();
        }
        // materializar entradas y normalizar nulos a ""
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.toList());

        int N = entries.size();
        // tokenización y TF por documento
        List<Map<String,Integer>> tfs = new ArrayList<>(N);
        List<Integer> docLens = new ArrayList<>(N);
        Map<String,Integer> df = new HashMap<>();
        for (Map.Entry<String,String> e : entries) {
            Map<String,Integer> tf = termFreq(e.getValue());
            tfs.add(tf);
            int len = tf.values().stream().mapToInt(Integer::intValue).sum();
            docLens.add(len);
            // DF por término
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum);
        }
        double avgDl = docLens.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        if (avgDl <= 0) avgDl = 1.0;

        // IDF clásico suavizado
        Map<String,Double> idf = new HashMap<>(df.size());
        for (Map.Entry<String,Integer> de : df.entrySet()) {
            int dfi = de.getValue();
            double val = Math.log((N - dfi + 0.5) / (dfi + 0.5) + 1.0); // +1 para mantener positivo
            idf.put(de.getKey(), val);
        }

        // precompute norm denominators per doc for speed? We'll compute inside scoring; size is small.

        // calcular puntajes crudos simétricos y acumular para normalización
        List<Double> rawScores = new ArrayList<>();
        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                long t1 = System.nanoTime();
                double sAB = bm25ScoreQueryOnDoc(tfs.get(i).keySet(), tfs.get(j), docLens.get(j), avgDl, idf);
                double sBA = bm25ScoreQueryOnDoc(tfs.get(j).keySet(), tfs.get(i), docLens.get(i), avgDl, idf);
                double s = (sAB + sBA) / 2.0;
                long t2 = System.nanoTime();
                rawScores.add(s);
                results.add(AlgorithmPairResult.builder()
                        .idA(entries.get(i).getKey())
                        .idB(entries.get(j).getKey())
                        .distance(0) // se rellena tras normalizar
                        .score(s)    // temporal: crudo
                        .similarityPercent(0)
                        .timeMs(Math.max(0L, (t2 - t1) / 1_000_000L))
                        .build());
            }
        }

        // normalizar [0..1] por min-max del run
        double min = rawScores.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = rawScores.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        boolean flat = (max - min) <= 1e-12;
        for (AlgorithmPairResult r : results) {
            double norm = flat ? 1.0 : (r.getScore() - min) / (max - min);
            double pct = Math.round(norm * 10000.0) / 100.0; // 2 decimales
            int dist = (int) Math.round((1.0 - norm) * 1000.0);
            r.setScore(norm);
            r.setSimilarityPercent(pct);
            r.setDistance(dist);
        }

        results.sort(Comparator
                .comparingDouble(AlgorithmPairResult::getScore).reversed()
                .thenComparing(AlgorithmPairResult::getIdA)
                .thenComparing(AlgorithmPairResult::getIdB));

        long total = System.currentTimeMillis() - t0;
        return AlgorithmRunResult.builder()
                .algorithm("BM25")
                .totalTimeMs(total)
                .totalComparisons(results.size())
                .results(results)
                .build();
    }

    // --- utilidades ---

    private Map<String,Integer> termFreq(String text) {
        Map<String,Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String p : parts) {
            if (p.length() >= 2) tf.merge(p, 1, Integer::sum);
        }
        return tf;
    }

    private double bm25ScoreQueryOnDoc(Set<String> queryTerms,
                                       Map<String,Integer> docTf,
                                       int docLen,
                                       double avgDl,
                                       Map<String,Double> idf) {
        if (queryTerms == null || queryTerms.isEmpty()) return 0.0;
        double score = 0.0;
        double norm = K1 * (1 - B + B * (docLen / avgDl));
        for (String t : queryTerms) {
            Integer tf = docTf.get(t);
            if (tf == null || tf == 0) continue;
            double idfVal = idf.getOrDefault(t, 0.0);
            double num = tf * (K1 + 1);
            double den = tf + norm;
            score += idfVal * (num / den);
        }
        if (score < 0) score = 0.0; // estabilidad
        return score;
    }
}

