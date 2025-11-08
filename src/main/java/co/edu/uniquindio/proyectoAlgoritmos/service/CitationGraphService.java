package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.TFIDFCosineSimilarity;
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

    public enum SimilarityMode { ABSTRACTS_TFIDF, COMBINED, KEYWORDS }

    private final ArticlesService articlesService;
    private final TFIDFCosineSimilarity tfidfCosineSimilarity;

    // Grafo en memoria
    private final Map<String, List<Edge>> adj = new HashMap<>();
    private final Map<String, ArticleDTO> nodes = new HashMap<>();
    private double lastThreshold = 0.15; // umbral por defecto
    private SimilarityMode lastMode = SimilarityMode.ABSTRACTS_TFIDF;

    @Data
    public static class Edge {
        private final String from;
        private final String to;
        private final double weight;     // 1 - similitud (>=0)
        private final double similarity; // [0..1]
    }

    // ---------------- Construcción del grafo ----------------

    public synchronized GraphBuildResult build(Double thresholdOpt, String modeOpt) {
        double threshold = thresholdOpt != null ? thresholdOpt : lastThreshold;
        SimilarityMode mode = parseMode(modeOpt, lastMode);
        lastThreshold = threshold;
        lastMode = mode;

        adj.clear();
        nodes.clear();

        List<ArticleDTO> list = articlesService.getArticles().getBody();
        if (list == null) list = List.of();

        // Indexar nodos
        for (ArticleDTO a : list) {
            if (a.getId() == null) continue;
            nodes.put(a.getId(), a);
            adj.put(a.getId(), new ArrayList<>());
        }
        if (nodes.isEmpty()) return new GraphBuildResult(0,0,threshold, mode.name(), getSimilarityLabel(mode));

        // Según el modo, obtenemos pares (idA, idB, similarity)
        List<PairSim> pairs;
        if (mode == SimilarityMode.ABSTRACTS_TFIDF) {
            pairs = tfidfPairs(nodes.values());
        } else if (mode == SimilarityMode.COMBINED) {
            pairs = combinedPairs(nodes.values());
        } else {
            pairs = keywordPairs(nodes.values());
        }

        // Crear aristas dirigidas con peso 1-sim y orientación por año (heurística)
        for (PairSim pr : pairs) {
            double sim = pr.sim;
            if (sim < threshold) continue;
            ArticleDTO a = nodes.get(pr.idA);
            ArticleDTO b = nodes.get(pr.idB);
            if (a == null || b == null) continue;

            double w = Math.max(0.0, 1.0 - sim);
            if (Objects.equals(a.getId(), b.getId())) continue; // evita self-loops

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
                // solo uno tiene año -> apuntar hacia el que SÍ tiene año (como "citado")
                if (ya != null) addEdge(b.getId(), a.getId(), w, sim);
                else addEdge(a.getId(), b.getId(), w, sim);
            }
        }

        int edges = adj.values().stream().mapToInt(List::size).sum();
        return new GraphBuildResult(nodes.size(), edges, threshold, mode.name(), getSimilarityLabel(mode));
    }

    // retro-compat
    public synchronized GraphBuildResult build(Double thresholdOpt) {
        return build(thresholdOpt, null);
    }

    private void addEdge(String from, String to, double w, double sim) {
        // evitar duplicados exactos (from->to con mismo peso/sim)
        List<Edge> out = adj.get(from);
        if (out != null) {
            for (Edge e : out) if (e.getTo().equals(to)) return;
            out.add(new Edge(from, to, w, sim));
        }
    }

    // ---------------- Similitud: utilidades ----------------

    private static class PairSim {
        final String idA, idB;
        final double sim;
        PairSim(String idA, String idB, double sim) { this.idA=idA; this.idB=idB; this.sim=sim; }
    }

    private List<PairSim> tfidfPairs(Collection<ArticleDTO> arts) {
        Map<String,String> idToText = new LinkedHashMap<>();
        for (ArticleDTO a : arts) idToText.put(a.getId(), a.getAbstractText()==null? "" : a.getAbstractText());
        var run = tfidfCosineSimilarity.tfidfCosineDistance(idToText);
        List<PairSim> out = new ArrayList<>();
        for (var pr : run.getResults()) out.add(new PairSim(pr.getIdA(), pr.getIdB(), pr.getScore()));
        return out;
    }

    private List<PairSim> combinedPairs(Collection<ArticleDTO> arts) {
        List<ArticleDTO> L = new ArrayList<>(arts);
        List<PairSim> out = new ArrayList<>();
        for (int i=0;i<L.size();i++) {
            for (int j=i+1;j<L.size();j++) {
                ArticleDTO a = L.get(i), b = L.get(j);
                double s = combinedSimilarity(a,b);
                out.add(new PairSim(a.getId(), b.getId(), s));
            }
        }
        return out;
    }

    private List<PairSim> keywordPairs(Collection<ArticleDTO> arts) {
        List<ArticleDTO> L = new ArrayList<>(arts);
        List<PairSim> out = new ArrayList<>();
        for (int i=0;i<L.size();i++) {
            for (int j=i+1;j<L.size();j++) {
                ArticleDTO a = L.get(i), b = L.get(j);
                double s = keywordsSimilarity(a,b);
                out.add(new PairSim(a.getId(), b.getId(), s));
            }
        }
        return out;
    }

    private String getSimilarityLabel(SimilarityMode mode) {
        return switch (mode) {
            case ABSTRACTS_TFIDF -> "similitud TF-IDF coseno (abstracts)";
            case COMBINED -> "0.6·Jaccard(título) + 0.2·Jaccard(autores) + 0.2·Jaccard(keywords)";
            case KEYWORDS -> "Jaccard(keywords)";
        };
    }

    public String getSimilarityLabel() { return getSimilarityLabel(lastMode); }

    private SimilarityMode parseMode(String modeOpt, SimilarityMode def) {
        if (modeOpt == null || modeOpt.isBlank()) return def;
        try { return SimilarityMode.valueOf(modeOpt.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignore) { return def; }
    }

    // Jaccard y normalizaciones (título/keywords/autores)
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
        Set<String> out = new LinkedHashSet<>();
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
        if (w.endsWith("s") && w.length()>3) w = w.substring(0, w.length()-1); // singularizar simple
        if (w.equals("ai")) return "artificialintelligence";
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
        return x.replaceAll("\\s+", " ").trim();
    }

    private double jaccard(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 0.0;
        int inter = 0;
        if (A.size() < B.size()) for (String a : A) if (B.contains(a)) inter++;
        else for (String b : B) if (A.contains(b)) inter++;
        int uni = A.size() + B.size() - inter;
        return uni==0 ? 0.0 : (double) inter / (double) uni;
    }

    // ---------------- Caminos mínimos ----------------

    /** Dijkstra: camino mínimo (distancia = suma de pesos) entre dos nodos */
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
            if (cur == null) break;
            path.add(cur);
        }
        Collections.reverse(path);
        double d = dist.getOrDefault(targetId, Double.POSITIVE_INFINITY);
        return new PathResult(path, d);
    }

    /** Floyd–Warshall: all-pairs (útil para análisis global). */
    public AllPairsResult allPairsShortestPaths() {
        List<String> ids = new ArrayList<>(nodes.keySet());
        ids.sort(Comparator.naturalOrder());
        int n = ids.size();

        double[][] D = new double[n][n];
        int[][] P = new int[n][n]; // predecessor indices (-1 = none)

        for (int i=0;i<n;i++) {
            Arrays.fill(D[i], Double.POSITIVE_INFINITY);
            Arrays.fill(P[i], -1);
            D[i][i] = 0.0;
        }

        // inicializar con aristas
        Map<String,Integer> idx = new HashMap<>();
        for (int i=0;i<n;i++) idx.put(ids.get(i), i);

        for (Map.Entry<String, List<Edge>> en : adj.entrySet()) {
            int u = idx.get(en.getKey());
            for (Edge e : en.getValue()) {
                Integer v = idx.get(e.getTo());
                if (v == null) continue;
                if (e.getWeight() < D[u][v]) {
                    D[u][v] = e.getWeight();
                    P[u][v] = u;
                }
            }
        }

        // relaxación
        for (int k=0;k<n;k++) {
            for (int i=0;i<n;i++) {
                if (D[i][k] == Double.POSITIVE_INFINITY) continue;
                for (int j=0;j<n;j++) {
                    double alt = D[i][k] + D[k][j];
                    if (alt < D[i][j]) {
                        D[i][j] = alt;
                        P[i][j] = P[k][j];
                    }
                }
            }
        }

        return new AllPairsResult(ids, D, P);
    }

    // ---------------- Componentes ----------------

    public Components components() {
        // Grafo no dirigido sin duplicados
        Map<String, Set<String>> und = new LinkedHashMap<>();
        for (String u : adj.keySet()) und.put(u, new LinkedHashSet<>());
        for (Map.Entry<String, List<Edge>> en : adj.entrySet()) {
            String u = en.getKey();
            for (Edge e : en.getValue()) {
                und.get(u).add(e.getTo());
                und.get(e.getTo()).add(u);
            }
        }

        // WCC con BFS
        List<List<String>> weak = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String v : und.keySet()) if (seen.add(v)) {
            List<String> comp = new ArrayList<>();
            Deque<String> dq = new ArrayDeque<>(); dq.add(v); comp.add(v);
            while (!dq.isEmpty()) {
                String x = dq.poll();
                for (String y : und.getOrDefault(x, Set.of())) {
                    if (seen.add(y)) { dq.add(y); comp.add(y); }
                }
            }
            comp.sort(Comparator.naturalOrder());
            weak.add(comp);
        }
        weak.sort(Comparator.<List<String>>comparingInt(List::size).reversed()
                .thenComparing(l -> l.isEmpty() ? "" : l.get(0)));

        // SCC con Tarjan
        List<List<String>> strong = tarjanSCC();
        for (List<String> comp : strong) comp.sort(Comparator.naturalOrder());
        strong.sort(Comparator.<List<String>>comparingInt(List::size).reversed()
                .thenComparing(l -> l.isEmpty() ? "" : l.get(0)));

        return new Components(weak, strong);
    }

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


    // ---------------- DTOs / Export ----------------

    @Data
    public static class GraphBuildResult {
        private final int nodes;
        private final int edges;
        private final double threshold;
        private final String mode;
        private final String similarity;
    }

    @Data
    public static class PathResult {
        private final List<String> path;
        private final double distance;
        public static PathResult notFound() { return new PathResult(List.of(), Double.POSITIVE_INFINITY); }
    }

    @Data
    public static class AllPairsResult {
        // ids[i] mapea índice -> id; dist[i][j] = distancia mínima i->j
        private final List<String> ids;
        private final double[][] dist;
        private final int[][] pred;
    }

    @Data
    public static class Components {
        private final List<List<String>> weak;
        private final List<List<String>> strong;
    }

    @Data @AllArgsConstructor
    public static class NodeRel {
        private String id;
        private String title;
        private Integer year;
    }

    @Data @AllArgsConstructor
    public static class EdgeRel {
        private String from;
        private String to;
        private double weight;
        private double similarity;
    }

    @Data @AllArgsConstructor
    public static class GraphExport {
        private List<NodeRel> nodes;
        private List<EdgeRel> edges;
    }

    public Map<String, List<Edge>> getAdjacency() { return Collections.unmodifiableMap(adj); }
    public Map<String, ArticleDTO> getNodes() { return Collections.unmodifiableMap(nodes); }

    public GraphExport exportRelationships() {
        List<NodeRel> ns = new ArrayList<>();
        for (Map.Entry<String, ArticleDTO> e : nodes.entrySet()) {
            ArticleDTO a = e.getValue();
            ns.add(new NodeRel(e.getKey(), a.getTitle(), a.getYear()));
        }
        List<EdgeRel> es = new ArrayList<>();
        for (List<Edge> lst : adj.values()) {
            for (Edge ed : lst) es.add(new EdgeRel(ed.getFrom(), ed.getTo(), ed.getWeight(), ed.getSimilarity()));
        }
        return new GraphExport(ns, es);
    }
}
