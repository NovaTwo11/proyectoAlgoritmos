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
}