package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.TFIDFCosineSimilarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmPairResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CitationGraphService {

    public enum SimilarityMode { COMBINED, KEYWORDS }

    private final ArticlesService articlesService;
    private final TFIDFCosineSimilarity tfidfCosineSimilarity;

    // Grafo en memoria
    private final Map<String, List<Edge>> adj = new HashMap<>();
    private final Map<String, ArticleDTO> nodes = new HashMap<>();
    private double lastThreshold = 0.15; // umbral 60% por requerimiento
    // Por requerimiento anterior: KEYWORDS (ya no se usa, pero se mantiene para compatibilidad)
    private SimilarityMode lastMode = SimilarityMode.KEYWORDS;

    @Data
    public static class Edge {
        private final String from;
        private final String to;
        private final double weight; // 1 - sim
        private final double similarity;
    }

    public synchronized GraphBuildResult build(Double thresholdOpt, String ignoredMode) {
        double threshold = thresholdOpt != null ? thresholdOpt : lastThreshold;
        lastThreshold = threshold;
        adj.clear(); nodes.clear();
        List<ArticleDTO> list = articlesService.getArticles().getBody();
        if (list == null) list = List.of();
        // index nodes
        for (ArticleDTO a : list) {
            if (a.getId()==null) continue;
            nodes.put(a.getId(), a);
            adj.put(a.getId(), new ArrayList<>());
        }
        if (nodes.isEmpty()) return new GraphBuildResult(0,0,threshold, getSimilarityLabel());

        // Construir mapa id -> abstract
        Map<String,String> idToText = new LinkedHashMap<>();
        for (ArticleDTO a : nodes.values()) {
            idToText.put(a.getId(), a.getAbstractText()==null? "" : a.getAbstractText());
        }
        // Calcular similitudes TF-IDF + coseno
        var run = tfidfCosineSimilarity.tfidfCosineDistance(idToText);
        List<AlgorithmPairResult> pairs = run.getResults();

        // Crear aristas según umbral y año
        for (AlgorithmPairResult pr : pairs) {
            double sim = pr.getScore();
            if (sim < threshold) continue;
            ArticleDTO a = nodes.get(pr.getIdA());
            ArticleDTO b = nodes.get(pr.getIdB());
            if (a==null || b==null) continue;
            double w = 1.0 - sim;
            Integer ya = a.getYear();
            Integer yb = b.getYear();
            if (ya != null && yb != null) {
                if (ya > yb) {
                    addEdge(a.getId(), b.getId(), w, sim);
                } else if (yb > ya) {
                    addEdge(b.getId(), a.getId(), w, sim);
                } else { // mismo año -> bidireccional
                    addEdge(a.getId(), b.getId(), w, sim);
                    addEdge(b.getId(), a.getId(), w, sim);
                }
            } else if (ya == null && yb == null) {
                // ambos sin año -> bidireccional
                addEdge(a.getId(), b.getId(), w, sim);
                addEdge(b.getId(), a.getId(), w, sim);
            } else {
                // solo uno tiene año -> apuntar al que tiene año
                if (ya != null) {
                    addEdge(b.getId(), a.getId(), w, sim);
                } else {
                    addEdge(a.getId(), b.getId(), w, sim);
                }
            }
        }
        int edges = adj.values().stream().mapToInt(List::size).sum();
        return new GraphBuildResult(nodes.size(), edges, threshold, getSimilarityLabel());
    }

    // Compatibilidad hacia atrás (sin modo)
    public synchronized GraphBuildResult build(Double thresholdOpt) {
        return build(thresholdOpt, null);
    }

    private void addEdge(String from, String to, double w, double sim) {
        adj.get(from).add(new Edge(from, to, w, sim));
    }

    // --- Similaridad ---
    private double combinedSimilarity(ArticleDTO a, ArticleDTO b) {
        Set<String> tA = tokenize(a.getTitle());
        Set<String> tB = tokenize(b.getTitle());
        double sTitle = jaccard(tA, tB);
        Set<String> kwA = normalizeList(a.getKeywords());
        Set<String> kwB = normalizeList(b.getKeywords());
        double sKw = jaccard(kwA, kwB);
        Set<String> auA = normalizeAuthors(a.getAuthors());
        Set<String> auB = normalizeAuthors(b.getAuthors());
        double sAu = jaccard(auA, auB);
        return 0.6*sTitle + 0.2*sAu + 0.2*sKw;
    }

    private double keywordsSimilarity(ArticleDTO a, ArticleDTO b) {
        Set<String> kwA = normalizeList(a.getKeywords());
        Set<String> kwB = normalizeList(b.getKeywords());
        return jaccard(kwA, kwB);
    }

    private Set<String> tokenize(String s) {
        if (s==null) return Set.of();
        String t = normalize(s);
        String[] parts = t.split("[^a-z0-9]+");
        Set<String> out = new HashSet<>();
        for (String p : parts) if (p.length()>=3) out.add(p);
        return out;
    }

    private Set<String> normalizeList(List<String> list) {
        if (list==null) return Set.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .map(x -> x.replace('_', ' '))
                .flatMap(x -> Arrays.stream(x.split("[^a-z0-9]+")))
                .map(this::normalizeVariant)
                .filter(w -> w.length()>=3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeVariant(String w) {
        // tratar plurales simples: ends with 's' -> singularizar (heurístico)
        if (w.endsWith("s") && w.length()>3) w = w.substring(0, w.length()-1);
        // unificar variantes típicas
        if (w.equals("ai")) return "artificialintelligence"; // compactar 'ai'
        return w;
    }

    private Set<String> normalizeAuthors(List<String> list) {
        if (list==null) return Set.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .map(x -> x.replaceAll("[^a-z ]+", " ").replaceAll("\\s+", " ").trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String s) {
        String x = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private double jaccard(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 0.0;
        int inter = 0;
        if (A.size() < B.size()) {
            for (String a : A) if (B.contains(a)) inter++;
        } else {
            for (String b : B) if (A.contains(b)) inter++;
        }
        int uni = A.size() + B.size() - inter;
        return uni==0 ? 0.0 : (double) inter / (double) uni;
    }

    // --- Dijkstra ---
    public PathResult shortestPath(String sourceId, String targetId) {
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(targetId)) return PathResult.notFound();
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        for (String v : nodes.keySet()) dist.put(v, Double.POSITIVE_INFINITY);
        dist.put(sourceId, 0.0);
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(sourceId);
        Set<String> visited = new HashSet<>();
        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (!visited.add(u)) continue;
            if (u.equals(targetId)) break;
            for (Edge e : adj.getOrDefault(u, List.of())) {
                double alt = dist.get(u) + e.getWeight();
                if (alt < dist.getOrDefault(e.getTo(), Double.POSITIVE_INFINITY)) {
                    dist.put(e.getTo(), alt);
                    prev.put(e.getTo(), u);
                    pq.add(e.getTo());
                }
            }
        }
        if (!prev.containsKey(targetId) && !sourceId.equals(targetId)) return PathResult.notFound();
        List<String> path = new ArrayList<>();
        String cur = targetId;
        path.add(cur);
        while (!cur.equals(sourceId)) {
            cur = prev.get(cur);
            if (cur==null) break;
            path.add(cur);
        }
        Collections.reverse(path);
        double d = dist.getOrDefault(targetId, Double.POSITIVE_INFINITY);
        return new PathResult(path, d);
    }

    // --- Componentes ---
    public Components components() {
        // Componentes débiles (convirtiendo a no dirigido)
        Map<String, List<String>> und = new HashMap<>();
        for (String u : adj.keySet()) und.put(u, new ArrayList<>());
        for (Map.Entry<String, List<Edge>> en : adj.entrySet()) {
            String u = en.getKey();
            for (Edge e : en.getValue()) { und.get(u).add(e.getTo()); und.get(e.getTo()).add(u); }
        }
        List<List<String>> weak = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String v : und.keySet()) if (seen.add(v)) {
            List<String> comp = new ArrayList<>();
            Deque<String> dq = new ArrayDeque<>(); dq.add(v); comp.add(v);
            while (!dq.isEmpty()) {
                String x = dq.poll();
                for (String y : und.getOrDefault(x, List.of())) if (seen.add(y)) { dq.add(y); comp.add(y); }
            }
            weak.add(comp);
        }

        // Componentes fuertemente conexas con Tarjan
        List<List<String>> strong = tarjanSCC();
        return new Components(weak, strong);
    }

    // Implementación de Tarjan para SCC
    private List<List<String>> tarjanSCC() {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> sccs = new ArrayList<>();
        final int[] idx = {0};

        for (String v : adj.keySet()) {
            if (!index.containsKey(v)) strongConnect(v, index, lowlink, stack, onStack, sccs, idx);
        }
        return sccs;
    }

    private void strongConnect(String v,
                               Map<String, Integer> index,
                               Map<String, Integer> lowlink,
                               Deque<String> stack,
                               Set<String> onStack,
                               List<List<String>> sccs,
                               int[] idx) {
        index.put(v, idx[0]);
        lowlink.put(v, idx[0]);
        idx[0]++;
        stack.push(v);
        onStack.add(v);

        for (Edge e : adj.getOrDefault(v, List.of())) {
            String w = e.getTo();
            if (!index.containsKey(w)) {
                strongConnect(w, index, lowlink, stack, onStack, sccs, idx);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (Objects.equals(lowlink.get(v), index.get(v))) {
            List<String> comp = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                comp.add(w);
            } while (!w.equals(v));
            sccs.add(comp);
        }
    }

    private void dfs1(String v, Set<String> vis, List<String> order) {
        vis.add(v);
        for (Edge e : adj.getOrDefault(v, List.of())) if (!vis.contains(e.getTo())) dfs1(e.getTo(), vis, order);
        order.add(v);
    }
    private void dfs2(String v, Set<String> vis, List<String> comp, Map<String, List<String>> radj) {
        vis.add(v); comp.add(v);
        for (String u : radj.getOrDefault(v, List.of())) if (!vis.contains(u)) dfs2(u, vis, comp, radj);
    }

    // --- DTO internos ---
    @Data
    public static class GraphBuildResult {
        private final int nodes;
        private final int edges;
        private final double threshold;
        private final String mode;
    }
    @Data
    public static class PathResult {
        private final List<String> path;
        private final double distance;
        public static PathResult notFound() { return new PathResult(List.of(), Double.POSITIVE_INFINITY); }
    }
    @Data
    public static class Components {
        private final List<List<String>> weak;
        private final List<List<String>> strong;
    }

    @Data
    @AllArgsConstructor
    public static class NodeRel {
        private String id;
        private String title;
        private Integer year;
    }

    @Data
    @AllArgsConstructor
    public static class EdgeRel {
        private String from;
        private String to;
        private double weight;
        private double similarity;
    }

    @Data
    @AllArgsConstructor
    public static class GraphExport {
        private List<NodeRel> nodes;
        private List<EdgeRel> edges;
    }

    // Accesores
    public Map<String, List<Edge>> getAdjacency() { return Collections.unmodifiableMap(adj); }
    public Map<String, ArticleDTO> getNodes() { return Collections.unmodifiableMap(nodes); }
    public SimilarityMode getLastMode() { return lastMode; }

    public String getSimilarityLabel() {
        return "similitud TF-IDF coseno de abstracts";
    }

    public GraphExport exportRelationships() {
        List<NodeRel> ns = new ArrayList<>();
        for (Map.Entry<String, ArticleDTO> e : nodes.entrySet()) {
            ArticleDTO a = e.getValue();
            ns.add(new NodeRel(e.getKey(), a.getTitle(), a.getYear()));
        }
        List<EdgeRel> es = new ArrayList<>();
        for (List<Edge> lst : adj.values()) {
            for (Edge ed : lst) {
                es.add(new EdgeRel(ed.getFrom(), ed.getTo(), ed.getWeight(), ed.getSimilarity()));
            }
        }
        return new GraphExport(ns, es);
    }
}
