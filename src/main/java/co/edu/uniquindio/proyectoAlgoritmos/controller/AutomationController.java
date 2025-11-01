package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.service.AnalyzeSimilarityService;
import co.edu.uniquindio.proyectoAlgoritmos.service.ArticlesService;
import co.edu.uniquindio.proyectoAlgoritmos.service.AutomationOrchestratorService;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.LevenshteinSimilarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/algoritmos")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationOrchestratorService orchestrator;
    private final ArticlesService articlesService;
    private final AnalyzeSimilarityService analyzeSimilarityService;

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


}
