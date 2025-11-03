package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CooccurrenceGraphService {

    private final ArticlesService articlesService;

    // Grafo no dirigido: co-ocurrencias por par (peso: número de documentos donde co-ocurren)
    private final Map<String, Map<String, Integer>> adj = new HashMap<>();
    // Vocabulario seleccionado (ids normalizados)
    private final Set<String> vocabulary = new LinkedHashSet<>();
    // Etiquetas para mostrar (norm -> label original)
    private final Map<String,String> labels = new HashMap<>();

    // Parámetros por defecto
    private int topKTerms = 60;      // número máximo de términos frecuentes a incluir
    private int minDf = 2;            // aparecer al menos en 2 documentos
    private double maxDfRatio = 0.9;  // descartar si aparece en >90% de documentos

    @Data
    @AllArgsConstructor
    public static class BuildResult {
        private int nodes;
        private int edges;
        private int topKTerms;
        private int minDf;
        private double maxDfRatio;
    }

    @Data
    @AllArgsConstructor
    public static class Edge {
        private String a;
        private String b;
        private int weight; // número de documentos donde co-ocurren
    }

    @Data
    @AllArgsConstructor
    public static class DegreeInfo {
        private String term;
        private int degree;   // vecinos únicos
        private int strength; // suma de pesos
    }

    @Data
    @AllArgsConstructor
    public static class GraphExport {
        private List<String> nodes;            // ids normalizados
        private List<Edge> edges;
        private Map<String,String> labels;     // norm -> etiqueta
        private List<String> nodesLabels;      // etiquetas en el mismo orden que nodes
    }

    // Construcción estándar (frecuencias documentales + límites)
    public synchronized BuildResult build(Integer topKOpt, Integer minDfOpt, Double maxDfRatioOpt) {
        if (topKOpt != null) topKTerms = Math.max(10, topKOpt);
        if (minDfOpt != null) minDf = Math.max(1, minDfOpt);
        if (maxDfRatioOpt != null) maxDfRatio = Math.min(1.0, Math.max(0.1, maxDfRatioOpt));

        adj.clear();
        vocabulary.clear();
        labels.clear();

        ResponseEntity<List<ArticleDTO>> resp = articlesService.getArticles();
        List<ArticleDTO> arts = resp.getBody() != null ? resp.getBody() : List.of();
        if (arts.isEmpty()) return new BuildResult(0,0, topKTerms, minDf, maxDfRatio);

        // 1) Tokenizar abstracts por documento + normalizar keywords
        List<Set<String>> docTerms = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (ArticleDTO a : arts) {
            Set<String> terms = new LinkedHashSet<>();
            // keywords explícitas
            terms.addAll(normalizeKeywords(a.getKeywords()));
            // términos del abstract
            terms.addAll(tokenizeAbstract(a.getAbstractText()));
            docTerms.add(terms);
            for (String t : terms) df.merge(t, 1, Integer::sum);
        }
        int N = docTerms.size();
        if (N == 0) return new BuildResult(0,0, topKTerms, minDf, maxDfRatio);

        // 2) Seleccionar vocabulario frecuente (aproximación a "términos frecuentes" de R3/R4)
        List<Map.Entry<String,Integer>> frequent = df.entrySet().stream()
                .filter(e -> e.getValue() >= minDf)
                .filter(e -> e.getValue() <= Math.ceil(maxDfRatio * N))
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topKTerms)
                .toList();
        for (var e : frequent) vocabulary.add(e.getKey());
        for (String v : vocabulary) labels.put(v, v); // etiqueta por defecto igual al id

        // 3) Construir co-ocurrencias por documento (solo términos en vocabulario)
        for (Set<String> terms : docTerms) {
            List<String> list = terms.stream().filter(vocabulary::contains).sorted().toList();
            int m = list.size();
            for (int i=0; i<m; i++) {
                for (int j=i+1; j<m; j++) {
                    addUndirectedEdge(list.get(i), list.get(j), 1);
                }
            }
        }

        int edgesCount = adj.values().stream().mapToInt(Map::size).sum() / 2; // no dirigido
        return new BuildResult(vocabulary.size(), edgesCount, topKTerms, minDf, maxDfRatio);
    }

    // Construir usando un vocabulario impuesto (R3+R4) con etiquetas opcionales (norm->label). Coincidencia por frases completas.
    public synchronized BuildResult buildWithVocabulary(Collection<String> fixedVocab, Map<String,String> labelOverrides) {
        adj.clear();
        vocabulary.clear();
        labels.clear();
        List<String> vocabList = new ArrayList<>();
        if (fixedVocab != null) {
            for (String t : fixedVocab) if (t != null && !t.isBlank()) {
                String norm = normalize(t);
                if (!norm.isBlank()) {
                    vocabList.add(norm);
                    // si hay etiqueta original, úsala
                    if (labelOverrides != null) {
                        String lab = labelOverrides.get(normalize(t));
                        if (lab != null && !lab.isBlank()) labels.put(norm, lab);
                    }
                }
            }
        }
        for (String v : new LinkedHashSet<>(vocabList)) vocabulary.add(v);
        // completa etiquetas faltantes con el propio id
        for (String v : vocabulary) labels.putIfAbsent(v, v);

        ResponseEntity<List<ArticleDTO>> resp = articlesService.getArticles();
        List<ArticleDTO> arts = resp.getBody() != null ? resp.getBody() : List.of();
        if (arts.isEmpty() || vocabulary.isEmpty()) return new BuildResult(vocabulary.size(), 0, 0, 0, 0);

        List<Set<String>> docTerms = new ArrayList<>();
        for (ArticleDTO a : arts) {
            String abs = a.getAbstractText() != null ? a.getAbstractText() : "";
            String kw = a.getKeywords() != null ? String.join(" ", a.getKeywords()) : "";
            String doc = (abs + " " + kw).trim();
            Set<String> present = matchTermsInDoc(doc, vocabulary);
            docTerms.add(present);
        }
        for (Set<String> terms : docTerms) {
            List<String> list = terms.stream().sorted().toList();
            for (int i=0;i<list.size();i++)
                for (int j=i+1;j<list.size();j++)
                    addUndirectedEdge(list.get(i), list.get(j), 1);
        }
        int edgesCount = adj.values().stream().mapToInt(Map::size).sum() / 2;
        return new BuildResult(vocabulary.size(), edgesCount, 0, 0, 0);
    }

    // Compatibilidad con el antiguo método
    public synchronized BuildResult buildWithVocabulary(Collection<String> fixedVocab) {
        return buildWithVocabulary(fixedVocab, null);
    }

    // Devuelve los términos del vocabulario que aparecen en el documento (normalizado), como frase completa entre espacios
    private Set<String> matchTermsInDoc(String rawDoc, Set<String> vocab) {
        String doc = normalize(rawDoc);
        // bordes artificiales para búsqueda por espacios
        String padded = " " + doc + " ";
        Set<String> present = new LinkedHashSet<>();
        for (String term : vocab) {
            if (term.isBlank()) continue;
            String needle = " " + term + " ";
            if (padded.contains(needle)) present.add(term);
        }
        return present;
    }

    private void addUndirectedEdge(String a, String b, int w) {
        if (a.equals(b)) return;
        adj.computeIfAbsent(a, k -> new HashMap<>());
        adj.computeIfAbsent(b, k -> new HashMap<>());
        adj.get(a).merge(b, w, Integer::sum);
        adj.get(b).merge(a, w, Integer::sum);
    }

    public GraphExport export() {
        List<String> nodes = new ArrayList<>(vocabulary);
        nodes.sort(String::compareTo);
        List<Edge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Map<String,Integer>> en : adj.entrySet()) {
            String u = en.getKey();
            for (Map.Entry<String,Integer> e2 : en.getValue().entrySet()) {
                String v = e2.getKey();
                String key = u.compareTo(v) < 0 ? u+"|"+v : v+"|"+u;
                if (seen.add(key)) edges.add(new Edge(u, v, e2.getValue()));
            }
        }
        edges.sort((x,y) -> Integer.compare(y.getWeight(), x.getWeight()));
        List<String> nodesLabels = nodes.stream().map(id -> labels.getOrDefault(id, id)).toList();
        return new GraphExport(nodes, edges, new LinkedHashMap<>(labels), nodesLabels);
    }

    public List<DegreeInfo> degrees() {
        List<DegreeInfo> list = new ArrayList<>();
        for (String u : vocabulary) {
            Map<String,Integer> neigh = adj.getOrDefault(u, Map.of());
            int degree = neigh.size();
            int strength = neigh.values().stream().mapToInt(Integer::intValue).sum();
            list.add(new DegreeInfo(u, degree, strength));
        }
        list.sort((a,b) -> {
            int c = Integer.compare(b.degree, a.degree);
            if (c!=0) return c;
            return Integer.compare(b.strength, a.strength);
        });
        return list;
    }

    public List<List<String>> components() {
        List<List<String>> comps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String v : vocabulary) if (seen.add(v)) {
            List<String> comp = new ArrayList<>();
            Deque<String> dq = new ArrayDeque<>(); dq.add(v); comp.add(v);
            while (!dq.isEmpty()) {
                String x = dq.poll();
                for (String y : adj.getOrDefault(x, Map.of()).keySet()) if (seen.add(y)) { dq.add(y); comp.add(y); }
            }
            comps.add(comp);
        }
        comps.sort((a,b) -> Integer.compare(b.size(), a.size()));
        return comps;
    }

    // --- Helpers de normalización ---
    private Set<String> normalizeKeywords(List<String> kws) {
        if (kws == null) return Set.of();
        return kws.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .flatMap(s -> Arrays.stream(s.split("[^a-z0-9]+")))
                .filter(t -> t.length() >= 3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> tokenizeAbstract(String abs) {
        if (abs == null) return Set.of();
        String s = normalize(abs);
        String[] parts = s.split("[^a-z0-9]+");
        Set<String> out = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 3 && !isStopword(p)) out.add(p);
        }
        return out;
    }

    private boolean isStopword(String w) { return STOP.contains(w); }

    public String normalize(String s) {
        String x = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        x = x.replaceAll("https?://\\S+", " ")
             .replaceAll("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", " ")
             .replaceAll("[^a-z0-9]+", " ")
             .replaceAll("\\s+", " ")
             .trim();
        return x;
    }

    private static final Set<String> STOP = Set.of(
            "the","and","for","with","that","this","from","are","was","were","have","has","had","but","not","you","your","their",
            "of","in","on","as","by","to","at","or","an","a","is","it","be","we","our","they","them","there","here",
            "de","la","el","los","las","y","con","para","que","del","en","un","una","es","son","por","se","no","como","al"
    );

    // Accesores para renderer
    public Map<String, Map<String, Integer>> getAdjacency() { return Collections.unmodifiableMap(adj); }
    public Set<String> getVocabulary() { return Collections.unmodifiableSet(vocabulary); }
    public Map<String,String> getLabelsMap() { return Collections.unmodifiableMap(labels); }
}
