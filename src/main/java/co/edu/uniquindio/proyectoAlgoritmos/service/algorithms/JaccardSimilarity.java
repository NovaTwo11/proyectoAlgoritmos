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
public class JaccardSimilarity {

    // Config: si ambos conjuntos quedan vacíos, retornar 1.0 (idénticos) o 0.0 (sin información).
    private static final boolean EMPTY_BOTH_IS_ONE = false; // para abstracts suele ser mejor 0.0

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
     * API estándar para el frontend: recibe un mapa id -> texto (abstract)
     * y retorna resultados de similitud Jaccard por pares, en [0,1].
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

        // Materializar entradas y normalizar nulos a ""
        List<Map.Entry<String,String>> entries = idToText.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.toList());

        // Tokenizar una vez por entrada (Unicode + sin diacríticos + stopwords)
        List<Set<String>> tokens = new ArrayList<>(entries.size());
        for (Map.Entry<String,String> e : entries) {
            tokens.add(tokenize(e.getValue()));
        }

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
                        .distance((int) Math.round((1.0 - s) * 1000.0)) // Jaccard distance * 1000
                        .score(s)                                       // [0,1]
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

    /**
     * Tokeniza con:
     *  - minúsculas
     *  - normalización Unicode NFKD y eliminación de diacríticos
     *  - split por no-letra/dígito Unicode
     *  - filtro de stopwords y tokens con longitud < 2
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();

        String lowered = text.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", ""); // quita diacríticos (á->a, ñ->n, etc.)

        // separar por no-letra/dígito Unicode
        String[] parts = normalized.split("[^\\p{L}\\p{N}]+");

        // LinkedHashSet preserva orden de inserción (útil para debug), y evita duplicados
        Set<String> set = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() < 2) continue;
            if (STOPWORDS.contains(p)) continue;
            set.add(p);
        }
        return set;
    }

    /**
     * Jaccard clásico sobre conjuntos (presence/absence).
     * Devuelve un valor en [0,1]. Si ambos vacíos:
     *  - retorna 1.0 si EMPTY_BOTH_IS_ONE=true,
     *  - en caso contrario 0.0.
     */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null) return 0.0;

        boolean aEmpty = a.isEmpty();
        boolean bEmpty = b.isEmpty();
        if (aEmpty && bEmpty) return EMPTY_BOTH_IS_ONE ? 1.0 : 0.0;
        if (aEmpty || bEmpty) return 0.0;

        int intersection = 0;
        // iterar sobre el más pequeño para eficiencia
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = a.size() > b.size() ? a : b;

        for (String t : small) {
            if (large.contains(t)) intersection++;
        }
        int union = a.size() + b.size() - intersection;
        if (union == 0) return EMPTY_BOTH_IS_ONE ? 1.0 : 0.0; // seguridad extra
        return intersection / (double) union;
    }
}
