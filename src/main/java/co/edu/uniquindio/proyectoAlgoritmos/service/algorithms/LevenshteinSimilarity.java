package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;

/**
 * Utilidades para calcular distancia y similitud (normalizada) entre cadenas usando Levenshtein.
 */
@Slf4j
@Service
public class LevenshteinSimilarity {

    // --- API para front con ids ---

    /**
     * Entrada id->texto (abstract). Internamente se procesa por índices (i<j),
     * y se devuelven los ids solamente en la respuesta para el front.
     */
    public AlgorithmRunResult levenshteinDistance(Map<String, String> idToText) {
        return compareAll(idToText);
    }

    /**
     * API estándar: recibe un mapa id->texto y retorna resultados comparados por pares con ids.
     */
    public AlgorithmRunResult compareAll(Map<String, String> idToText) {
        long t0 = System.currentTimeMillis();
        if (idToText == null || idToText.isEmpty()) {
            return AlgorithmRunResult.builder()
                    .algorithm("Levenshtein")
                    .totalTimeMs(0)
                    .totalComparisons(0)
                    .results(Collections.emptyList())
                    .build();
        }
        // materializar entradas y normalizar nulos a ""
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.toList());

        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                String idA = entries.get(i).getKey();
                String idB = entries.get(j).getKey();
                String a = entries.get(i).getValue();
                String b = entries.get(j).getValue();
                long t1 = System.nanoTime();
                int d = distance(a, b);
                double s = similarityFromDistance(d, a.length(), b.length());
                long t2 = System.nanoTime();
                results.add(AlgorithmPairResult.builder()
                        .idA(idA)
                        .idB(idB)
                        .distance(d)
                        .score(s)
                        .similarityPercent(Math.round(s * 10000.0) / 100.0) // dos decimales
                        .timeMs(Math.max(0L, (t2 - t1) / 1_000_000L))
                        .build());
            }
        }
        // ordenar por score desc, luego por idA, idB
        results.sort(Comparator
                .comparingDouble(AlgorithmPairResult::getScore).reversed()
                .thenComparing(AlgorithmPairResult::getIdA)
                .thenComparing(AlgorithmPairResult::getIdB));

        long total = System.currentTimeMillis() - t0;
        return AlgorithmRunResult.builder()
                .algorithm("Levenshtein")
                .totalTimeMs(total)
                .totalComparisons(results.size())
                .results(results)
                .build();
    }

    // --- API por índices (útil internamente o pruebas) ---

    public static class Result {
        public final int i;          // índice primera cadena
        public final int j;          // índice segunda cadena
        public final String a;       // valor i
        public final String b;       // valor j
        public final int distance;   // distancia de Levenshtein
        public final double score;   // similitud normalizada [0..1]

        public Result(int i, int j, String a, String b, int distance, double score) {
            this.i = i;
            this.j = j;
            this.a = a;
            this.b = b;
            this.distance = distance;
            this.score = score;
        }

        @Override public String toString() {
            return "Result{" +
                    "i=" + i +
                    ", j=" + j +
                    ", distance=" + distance +
                    ", score=" + score +
                    ", a='" + a + '\'' +
                    ", b='" + b + '\'' +
                    '}';
        }
    }

    public List<Result> similarities(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        List<String> list = new ArrayList<>(values.size());
        for (String v : values) list.add(v == null ? "" : v);

        List<Result> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                String a = list.get(i);
                String b = list.get(j);
                int d = distance(a, b);
                double s = similarityFromDistance(d, a.length(), b.length());
                out.add(new Result(i, j, a, b, d, s));
            }
        }
        out.sort(Comparator.comparingDouble((Result r) -> r.score).reversed()
                .thenComparingInt(r -> r.i)
                .thenComparingInt(r -> r.j));
        return out;
    }

    public static double[][] similarityMatrix(List<String> values) {
        if (values == null) return new double[0][0];
        int n = values.size();
        double[][] M = new double[n][n];
        List<String> list = new ArrayList<>(n);
        for (String v : values) list.add(Objects.toString(v, ""));
        for (int i = 0; i < n; i++) M[i][i] = 1.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int d = distance(list.get(i), list.get(j));
                double s = similarityFromDistance(d, list.get(i).length(), list.get(j).length());
                M[i][j] = M[j][i] = s;
            }
        }
        return M;
    }

    public static int distance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        if (n > m) { String t = a; a = b; b = t; int tmp = n; n = m; m = tmp; }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;
                int del = prev[j] + 1;
                int ins = curr[j - 1] + 1;
                int sub = prev[j - 1] + cost;
                curr[j] = Math.min(Math.min(del, ins), sub);
            }
            int[] t = prev; prev = curr; curr = t;
        }
        return prev[m];
    }

    public static double similarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int d = distance(a, b);
        return similarityFromDistance(d, a.length(), b.length());
    }

    private static double similarityFromDistance(int distance, int lenA, int lenB) {
        int max = Math.max(lenA, lenB);
        if (max == 0) return 1.0;
        double s = 1.0 - (distance / (double) max);
        if (s < 0) s = 0.0;
        return s;
    }
}
