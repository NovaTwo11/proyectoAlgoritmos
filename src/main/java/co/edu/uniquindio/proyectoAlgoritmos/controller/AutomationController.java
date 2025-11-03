package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.KeywordAnalysisResponse;
import co.edu.uniquindio.proyectoAlgoritmos.service.*;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@RequiredArgsConstructor
@RequestMapping("/api/algoritmos")
@RestController
@Slf4j
public class AutomationController {

    private final AutomationOrchestratorService orchestrator;
    private final ArticlesService articlesService;
    private final AnalyzeSimilarityService analyzeSimilarityService;
    private final KeywordAnalysisService keywordAnalysisService;
    private final Requirement4OrchestratorService r4;
    private final CitationGraphService citationGraphService;
    private final CitationGraphRendererService citationGraphRendererService;
    private final CooccurrenceGraphService cooccurrenceGraphService;
    private final CooccurrenceGraphRendererService cooccurrenceGraphRendererService;
    // Servicios usados por /coocurrence/build-fixed
    private final PreprocessingPipelineService preprocessingPipelineService;
    private final SimilarityService similarityService;
    private final HierarchicalClusteringCore hclust;

    @PostMapping("/download-articles")
    public ResponseEntity<String> runReq1(@RequestParam(required = false) String query) {
        return orchestrator.downloadArticles(query);
    }

    @GetMapping("/get-articles")
    public ResponseEntity<List<ArticleDTO>> getArticles() {
        return articlesService.getArticles();
    }

    @PostMapping("/similarity-analyze")
    public ResponseEntity<List<AlgorithmRunResult>> similarityAnalyze(@RequestBody List<ArticleDTO> articles) {
        return analyzeSimilarityService.analyzeSimilarities(articles);
    }

    // Requerimiento 3 - sin parámetros: categoría y palabras están "quemadas" en el servicio
    @GetMapping("/keyword-analysis")
    public ResponseEntity<KeywordAnalysisResponse> analyzeKeywords() {
        ResponseEntity<List<ArticleDTO>> articlesResp = articlesService.getArticles();
        List<ArticleDTO> articles = articlesResp.getBody() != null ? articlesResp.getBody() : List.of();
        return keywordAnalysisService.analyze(articles);
    }

    // Requerimiento 4 - retorna las 3 imágenes (single, complete, average) en base64 + filePath
    @GetMapping("/dendrograma")
    public ResponseEntity<?> generateDendrogram() {
        List<ArticleDTO> articles = articlesService.getArticles().getBody();
        if (articles == null) articles = List.of();
        return r4.run(articles);
    }

    /**
     * Seguimiento 2
     */

    // Requerimiento 1 - construir grafo de citaciones (inferidas por similitud)
    @PostMapping("/citations/build")
    public ResponseEntity<?> buildCitationGraph(@RequestParam(required = false) Double threshold) {
        var result = citationGraphService.build(threshold);
        var export = citationGraphService.exportRelationships();
        // Generar imagen como efecto colateral (no se retorna base64)
        var render = citationGraphRendererService.renderGraph(null);
        return ResponseEntity.ok(java.util.Map.of(
                "graph", result,
                "relationships", export,
                "image", java.util.Map.of(
                        "filePath", render.filePath
                )
        ));
    }

    // Requerimiento 1 - camino mínimo (Dijkstra) entre dos artículos (ids)
    @GetMapping("/citations/shortest")
    public ResponseEntity<CitationGraphService.PathResult> shortestPath(@RequestParam String sourceId, @RequestParam String targetId) {
        return ResponseEntity.ok(citationGraphService.shortestPath(sourceId, targetId));
    }

    // Requerimiento 1 - componentes (débiles y fuertes)
    @GetMapping("/citations/components")
    public ResponseEntity<CitationGraphService.Components> components() {
        return ResponseEntity.ok(citationGraphService.components());
    }

    // Requerimiento 2 (Seguimiento 2) - Construcción del grafo de coocurrencia usando R3+R4 (garantiza 30 de R3)
    @PostMapping("/coocurrence/build")
    public ResponseEntity<?> buildCooccurrence() {
        return ResponseEntity.ok(buildCooccurrenceR3R4());
    }

    // Variante explícita: construir con vocabulario fijo (R3 + R4)
    @PostMapping("/coocurrence/build-fixed")
    public ResponseEntity<?> buildCooccurrenceFixed() {
        return ResponseEntity.ok(buildCooccurrenceR3R4());
    }

    // Helper local compartido para construir R3+R4
    private Map<String,Object> buildCooccurrenceR3R4() {
        // 1) Obtener artículos
        List<ArticleDTO> articles = articlesService.getArticles().getBody();
        if (articles == null) articles = List.of();
        // 2) R3: términos fijos + descubiertos
        var r3 = keywordAnalysisService.analyze(articles).getBody();
        List<String> fixedR3 = new ArrayList<>();
        Map<String,String> labelMap = new LinkedHashMap<>();
        if (r3 != null) {
            if (r3.getGivenKeywordFrequencies()!=null) {
                r3.getGivenKeywordFrequencies().forEach(tf -> {
                    fixedR3.add(tf.getTerm());
                    labelMap.put(cooccurrenceGraphService.normalize(tf.getTerm()), tf.getTerm());
                });
            }
            if (r3.getDiscoveredKeywords()!=null) {
                r3.getDiscoveredKeywords().forEach(tf -> {
                    fixedR3.add(tf.getTerm());
                    labelMap.put(cooccurrenceGraphService.normalize(tf.getTerm()), tf.getTerm());
                });
            }
        }
        // 3) R4: top términos por clúster (reusar preprocesamiento y similitud)
        var pre = preprocessingPipelineService.preprocessArticles(articles).getBody();
        List<String> fixedR4 = new ArrayList<>();
        if (pre != null && pre.getArticles()!=null && !pre.getArticles().isEmpty()) {
            var sim = similarityService.computeTfidfCosineAndEuclidean(pre.getArticles());
            var labels = sim.getLabels();
            var vecs = sim.getTfidfVectors();
            if (labels!=null && vecs!=null && labels.size()==vecs.size()) {
                double[][] dist = sim.getDistancesEuclidean();
                var merges = hclust.agglomerate(dist, HierarchicalClusteringCore.Linkage.AVERAGE);
                List<Set<Integer>> clusters = cutIntoK(merges, labels.size(), 3);
                for (Set<Integer> cl : clusters) {
                    Map<String, Double> scores = new HashMap<>();
                    for (int idx : cl) {
                        Map<String, Double> v = vecs.get(idx);
                        for (var e : v.entrySet()) scores.merge(e.getKey(), e.getValue(), Double::sum);
                    }
                    scores.entrySet().stream()
                            .sorted((a,b) -> Double.compare(b.getValue(), a.getValue()))
                            .limit(10)
                            .forEach(e -> {
                                fixedR4.add(e.getKey());
                                labelMap.putIfAbsent(cooccurrenceGraphService.normalize(e.getKey()), e.getKey());
                            });
                }
            }
        }
        // 4) Vocabulario combinado y construcción (garantizar 30 de R3 siempre presentes en nodos)
        Set<String> vocab = new LinkedHashSet<>();
        vocab.addAll(fixedR3); // 30 de R3
        vocab.addAll(fixedR4); // top por clúster de R4
        var result = cooccurrenceGraphService.buildWithVocabulary(vocab, labelMap);
        var export = cooccurrenceGraphService.export();
        var render = cooccurrenceGraphRendererService.render(null);
        return java.util.Map.of(
                "graph", result,
                "export", export,
                "image", java.util.Map.of("filePath", render.filePath),
                "vocabularySize", vocab.size()
        );
    }

    // Helper local para cortar dendrograma en K clusters
    private List<Set<Integer>> cutIntoK(List<HierarchicalClusteringCore.Merge> merges, int n, int K) {
        // Inicialmente cada punto es su propio cluster
        List<Set<Integer>> clusters = new ArrayList<>();
        for (int i=0;i<n;i++) clusters.add(new LinkedHashSet<>(java.util.List.of(i)));
        int nextIndex = n;
        for (HierarchicalClusteringCore.Merge m : merges) {
            // encontrar sets vivos que contienen left/right
            int ai = findClusterIndex(clusters, m.left);
            int bi = findClusterIndex(clusters, m.right);
            if (ai==-1 || bi==-1 || ai==bi) continue;
            Set<Integer> merged = new LinkedHashSet<>(clusters.get(ai)); merged.addAll(clusters.get(bi));
            // reemplazar ai y bi por merged
            int i1 = Math.max(ai, bi), i2 = Math.min(ai, bi);
            clusters.remove(i1);
            clusters.remove(i2);
            clusters.add(merged);
            nextIndex++;
            if (clusters.size() <= K) break;
        }
        // si aún hay más de K, unir arbitrariamente
        while (clusters.size() > K) {
            Set<Integer> a = clusters.remove(clusters.size()-1);
            clusters.get(0).addAll(a);
        }
        return clusters;
    }
    private int findClusterIndex(List<Set<Integer>> clusters, int idx) {
        for (int i=0;i<clusters.size();i++) if (clusters.get(i).contains(idx)) return i;
        return -1;
    }
}
