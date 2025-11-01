package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.LevenshteinSimilarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.JaccardSimilarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.TFIDFCosineSimilarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.BM25Similarity;
import co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto.AlgorithmRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeSimilarityService {

    private final LevenshteinSimilarity levenshteinSimilarity;
    private final JaccardSimilarity jaccardSimilarity;
    private final TFIDFCosineSimilarity tfidfCosineSimilarity;
    private final BM25Similarity bm25Similarity;

    public ResponseEntity<List<AlgorithmRunResult>> analyzeSimilarities(List<ArticleDTO> articles) {
        List<AlgorithmRunResult> algorithmRunResults = new ArrayList<>();
        Map<String, String> abstracts = createAbstractsMap(articles);

        algorithmRunResults.add(levenshteinSimilarity.levenshteinDistance(abstracts));
        algorithmRunResults.add(jaccardSimilarity.jaccardDistance(abstracts));
        algorithmRunResults.add(tfidfCosineSimilarity.tfidfCosineDistance(abstracts));
        algorithmRunResults.add(bm25Similarity.bm25Distance(abstracts));

        log.info("Finished analyzing similarities");
        log.info("Total de resultados: {}", algorithmRunResults.size());
        return ResponseEntity.ok(algorithmRunResults);
    }

    private Map<String, String> createAbstractsMap(List<ArticleDTO> articles) {
        Map<String, String> abstracts = new LinkedHashMap<>();

        for (ArticleDTO dto : articles) {
            String id = dto.getId();
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            String text = dto.getAbstractText();
            abstracts.put(id, text);
        }
        return abstracts;
    }
}