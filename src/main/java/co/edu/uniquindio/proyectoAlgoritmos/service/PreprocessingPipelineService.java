package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessedArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingOptions;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PreprocessingPipelineService {

    private final TextPreprocessingService textPreprocessingService;

    public ResponseEntity<PreprocessingResponse> preprocessArticles(List<ArticleDTO> articles) {
        PreprocessingOptions opts = PreprocessingOptions.builder()
                .keepPlus(true)
                .useBigrams(false)
                .removeNumbers(true)
                .minTokenLength(3)
                .build();

        int inputCount = articles != null ? articles.size() : 0;
        Map<String, PreprocessedArticleDTO> dedup = new LinkedHashMap<>();

        if (articles != null) {
            for (ArticleDTO a : articles) {
                String id = a.getId() != null ? a.getId() : UUID.randomUUID().toString();
                String title = a.getTitle() != null ? a.getTitle() : "";
                String abs = a.getAbstractText();
                if (abs == null) abs = "";
                String norm = textPreprocessingService.normalize(abs);
                List<String> tokens = textPreprocessingService.tokenize(norm, opts);
                tokens = textPreprocessingService.filterTokens(tokens, opts);
                tokens = textPreprocessingService.maybeBigrams(tokens, opts);
                if (tokens.isEmpty()) continue;
                String dedupKey = normalizeTitle(title);
                dedup.putIfAbsent(dedupKey, new PreprocessedArticleDTO(id, title, norm, tokens));
            }
        }

        List<PreprocessedArticleDTO> out = new ArrayList<>(dedup.values());
        PreprocessingResponse resp = PreprocessingResponse.builder()
                .inputCount(inputCount)
                .outputCount(out.size())
                .dedupCriteria("title: normalized")
                .options(opts)
                .articles(out)
                .build();
        return ResponseEntity.ok(resp);
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        String t = Normalizer.normalize(title.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return t.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }
}

