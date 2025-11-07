package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Similaridad coseno sobre vectores BM25 (Okapi-tf * idf) por documento.
 * - Construye un vector por doc: w_t = idf(t) * ((tf_t*(K1+1)) / (tf_t + K1*(1 - B + B*|d|/avgdl))).
 * - Normaliza L2 y usa coseno (producto punto).
 * - Devuelve score en [0,1] sin calibración por lote.
 *
 * Nota: Para mantener cosenos en [0,1], se clampa IDF a >= 0 (idfNegativesToZero).
 */
@Slf4j
@Service
public class BM25CosineSimilarity {

    private static final double K1 = 1.5;
    private static final double B  = 0.75;

    // IDF negativo puede producir pesos negativos y cosenos negativos; lo evitamos clampeando a >= 0.
    private static final boolean IDF_NONNEGATIVE = true;

    // Stopwords ES/EN (amplía si lo necesitas)
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // Español
            "a","ante","bajo","con","contra","de","del","desde","durante","en","entre","hacia","hasta","para","por","segun","sin","sobre","tras",
            "el","la","los","las","un","una","unos","unas","y","o","u","que","como","si","no","al","lo","su","sus","se","es","son","ser","fue","fueron",
            "este","esta","estos","estas","esto","ese","esa","esos","esas","eso","mi","mis","tu","tus","ya","muy","mas","menos","tambien",
            // Inglés
            "a","an","the","and","or","but","if","then","else","when","while","to","of","in","on","for","from","by","with","about","as","into","like",
            "through","after","over","between","out","against","during","without","before","under","around","among","is","are","was","were","be","been",
            "being","it","its","this","that","these","those","he","she","they","we","you","i","me","him","her","them","us","my","your","our","their"
    ));

    /**
     * API: recibe id->texto y retorna pares con similitud coseno BM25 en [0,1].
     */
    public AlgorithmRunResult bm25CosineDistance(Map<String, String> idToText) {
        long t0 = System.currentTimeMillis();
        if (idToText == null || idToText.isEmpty()) {
            return AlgorithmRunResult.builder()
                    .algorithm("BM25_COSINE")
                    .totalTimeMs(0)
                    .totalComparisons(0)
                    .results(Collections.emptyList())
                    .build();
        }

        // 0) Entradas (normaliza nulos)
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), Objects.toString(e.getValue(), "")))
                .collect(Collectors.toList());

        int N = entries.size();

        // 1) TF por documento + longitudes + DF
        List<Map<String,Integer>> tfs = new ArrayList<>(N);
        List<Integer> docLens = new ArrayList<>(N);
        Map<String,Integer> df = new HashMap<>();

        for (Map.Entry<String,String> e : entries) {
            Map<String,Integer> tf = termFreq(e.getValue());
            tfs.add(tf);
            int len = tf.values().stream().mapToInt(Integer::intValue).sum();
            docLens.add(len);
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum);
        }

        double avgDl = docLens.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        if (avgDl <= 0) avgDl = 1.0;

        // 2) IDF clásico; clamp >=0 si se desea coseno en [0,1]
        Map<String,Double> idf = new HashMap<>(df.size());
        for (Map.Entry<String,Integer> de : df.entrySet()) {
            int dfi = de.getValue();
            double val = Math.log((N - dfi + 0.5) / (dfi + 0.5)); // puede ser negativo
            if (IDF_NONNEGATIVE && val < 0) val = 0.0;
            idf.put(de.getKey(), val);
        }

        // 3) Vector BM25 por documento (Okapi-tf * idf) y normalización L2
        List<Map<String,Double>> vectors = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            Map<String,Integer> tf = tfs.get(i);
            int dl = docLens.get(i);
            double normDen = K1 * (1 - B + B * (dl / avgDl));
            Map<String,Double> vec = new HashMap<>(tf.size());
            double sumSq = 0.0;
            for (Map.Entry<String,Integer> te : tf.entrySet()) {
                String t = te.getKey();
                int f = te.getValue();
                double idfVal = idf.getOrDefault(t, 0.0);
                if (idfVal == 0.0 || f == 0) continue;
                double okapiTf = (f * (K1 + 1.0)) / (f + normDen);
                double w = idfVal * okapiTf;
                if (w == 0.0) continue;
                vec.put(t, w);
                sumSq += w * w;
            }
            if (sumSq > 0) {
                double inv = 1.0 / Math.sqrt(sumSq);
                for (Map.Entry<String,Double> e : vec.entrySet()) {
                    e.setValue(e.getValue() * inv);
                }
            } else {
                vec = Collections.emptyMap(); // vector vacío
            }
            vectors.add(vec);
        }

        // 4) Coseno entre pares (producto punto de vectores L2-normalizados)
        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                String idA = entries.get(i).getKey();
                String idB = entries.get(j).getKey();
                Map<String,Double> a = vectors.get(i);
                Map<String,Double> b = vectors.get(j);
                long t1 = System.nanoTime();
                double s = dot(a, b); // [0,1]
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

        results.sort(
                Comparator.comparingDouble(AlgorithmPairResult::getScore)
                        .reversed()
                        .thenComparing(AlgorithmPairResult::getIdA)
                        .thenComparing(AlgorithmPairResult::getIdB)
        );

        long total = System.currentTimeMillis() - t0;
        return AlgorithmRunResult.builder()
                .algorithm("BM25_COSINE")
                .totalTimeMs(total)
                .totalComparisons(results.size())
                .results(results)
                .build();
    }

    // --- utilidades ---

    /**
     * Tokeniza en minúsculas, normaliza Unicode (quita diacríticos),
     * separa por no-letra/dígito Unicode, filtra stopwords y tokens < 2.
     */
    private Map<String,Integer> termFreq(String text) {
        Map<String,Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;

        String lowered = text.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", ""); // elimina diacríticos

        String[] parts = normalized.split("[^\\p{L}\\p{N}]+");
        for (String p : parts) {
            if (p.length() < 2) continue;
            if (STOPWORDS.contains(p)) continue;
            tf.merge(p, 1, Integer::sum);
        }
        return tf;
    }

    private double dot(Map<String,Double> a, Map<String,Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Map<String,Double> small = a.size() <= b.size() ? a : b;
        Map<String,Double> large = a.size() > b.size() ? a : b;
        double sum = 0.0;
        for (Map.Entry<String,Double> e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) sum += e.getValue() * w;
        }
        // seguridad numérica
        if (sum < 0) sum = 0.0;
        if (sum > 1) sum = 1.0;
        return sum;
    }
}
