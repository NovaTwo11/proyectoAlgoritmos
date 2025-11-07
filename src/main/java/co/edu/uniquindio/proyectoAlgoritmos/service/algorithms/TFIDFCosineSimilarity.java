package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms;

import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TFIDFCosineSimilarity {

    // Stopwords mínimas ES/EN (amplía si lo necesitas).
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

        // 0) Materializar entradas, normalizar nulos y pre-normalizar texto
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), normalize(Objects.toString(e.getValue(), ""))))
                .collect(Collectors.toList());

        // 1) TF por documento y DF por término
        int N = entries.size();
        List<Map<String, Integer>> tfs = new ArrayList<>(N);
        Map<String, Integer> df = new HashMap<>();
        for (Map.Entry<String,String> e : entries) {
            Map<String, Integer> tf = termFreq(e.getValue());
            tfs.add(tf);
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum); // DF por presencia
        }

        // 2) IDF suavizado (no negativo), estándar y estable: idf = ln((N+1)/(df+1)) + 1
        Map<String, Double> idf = new HashMap<>(df.size());
        for (Map.Entry<String,Integer> dfe : df.entrySet()) {
            int dfi = dfe.getValue();
            double val = Math.log((N + 1.0) / (dfi + 1.0)) + 1.0;
            idf.put(dfe.getKey(), val);
        }

        // 3) Vectores TF-IDF normalizados (L2)
        List<Map<String, Double>> vectors = new ArrayList<>(N);
        for (Map<String, Integer> tf : tfs) {
            if (tf.isEmpty()) { vectors.add(Collections.emptyMap()); continue; }
            Map<String, Double> vec = new HashMap<>(tf.size());
            double sumSquares = 0.0;
            for (Map.Entry<String,Integer> te : tf.entrySet()) {
                Double idfVal = idf.get(te.getKey());
                if (idfVal == null) continue;
                // TF sublineal seguro: log1p(tf)
                double tfWeight = Math.log1p(te.getValue());
                double w = tfWeight * idfVal;
                if (w == 0.0) continue;
                vec.put(te.getKey(), w);
                sumSquares += w * w;
            }
            if (sumSquares > 0) {
                double inv = 1.0 / Math.sqrt(sumSquares);
                for (Map.Entry<String, Double> ve : vec.entrySet()) {
                    ve.setValue(ve.getValue() * inv);
                }
            } else {
                vec = Collections.emptyMap(); // todos pesos cero => vector vacío
            }
            vectors.add(vec);
        }

        // 4) Comparar pares (coseno = producto punto porque están normalizados)
        List<AlgorithmPairResult> results = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                String idA = entries.get(i).getKey();
                String idB = entries.get(j).getKey();
                Map<String, Double> a = vectors.get(i);
                Map<String, Double> b = vectors.get(j);
                long t1 = System.nanoTime();
                double s = dot(a, b); // [0,1] con TF-IDF no negativos
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

    // Normaliza a minúsculas, NFKD (sin diacríticos) y separa por no letra/dígito Unicode; filtra stopwords y tokens <2
    private Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;
        String[] parts = text.split("[^\\p{L}\\p{N}]+"); // ya viene lower y sin diacríticos desde normalize()
        for (String p : parts) {
            if (p.length() < 2) continue;
            if (STOPWORDS.contains(p)) continue;
            tf.merge(p, 1, Integer::sum);
        }
        return tf;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String lowered = s.toLowerCase(Locale.ROOT).trim();
        String nfkd = Normalizer.normalize(lowered, Normalizer.Form.NFKD);
        String withoutDiacritics = nfkd.replaceAll("\\p{M}", "");
        String cleaned = withoutDiacritics.replaceAll("[\\p{Cntrl}]", " ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    // Producto punto sobre mapas dispersos; si algún vector es vacío, devolvemos 0.0 (sin información)
    private double dot(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0; // evita 1.0 para “ambos vacíos”
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = a.size() > b.size() ? a : b;
        double sum = 0.0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) sum += e.getValue() * w;
        }
        // Con pesos no negativos, el coseno ya cae en [0,1]; deja clamps por seguridad numérica
        if (sum < 0) sum = 0.0;
        if (sum > 1) sum = 1.0;
        return sum;
    }
}
