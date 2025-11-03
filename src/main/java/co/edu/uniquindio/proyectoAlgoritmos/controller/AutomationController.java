package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.KeywordAnalysisResponse;
import co.edu.uniquindio.proyectoAlgoritmos.service.*;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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

}