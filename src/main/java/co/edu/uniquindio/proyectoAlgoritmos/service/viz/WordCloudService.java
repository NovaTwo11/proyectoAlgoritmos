package co.edu.uniquindio.proyectoAlgoritmos.service.viz;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingOptions;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.TermFrequency;
import co.edu.uniquindio.proyectoAlgoritmos.service.articles.ArticlesService;
import co.edu.uniquindio.proyectoAlgoritmos.service.articles.TextPreprocessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final ArticlesService articlesService;
    private final TextPreprocessingService textPreprocessingService;

    private static final int DEFAULT_LIMIT = 150; // número máximo de términos en la nube

    public ResponseEntity<List<TermFrequency>> buildWordCloud(Integer limit) {
        List<ArticleDTO> articles = articlesService.getArticles().getBody();
        if (articles == null) articles = List.of();
        int totalDocs = articles.size();
        if (totalDocs == 0) return ResponseEntity.ok(List.of());

        PreprocessingOptions opts = PreprocessingOptions.builder()
                .keepPlus(true)
                .useBigrams(false)
                .removeNumbers(true)
                .minTokenLength(3)
                .build();

        Map<String,Integer> freq = new HashMap<>();

        for (ArticleDTO a : articles) {
            // Procesar abstract
            String abs = a.getAbstractText();
            if (abs != null && !abs.isBlank()) {
                String norm = textPreprocessingService.normalize(abs);
                List<String> tokens = textPreprocessingService.tokenize(norm, opts);
                tokens = textPreprocessingService.filterTokens(tokens, opts);
                // no bigrams para nube inicial
                for (String t : tokens) freq.merge(t, 1, Integer::sum);
            }
            // Procesar keywords (agregar como tokens individuales)
            if (a.getKeywords() != null) {
                for (String kw : a.getKeywords()) {
                    String normKw = normalizeKeyword(kw);
                    if (normKw.isBlank()) continue;
                    // separar en tokens si hay espacios
                    for (String token : normKw.split(" ")) {
                        if (token.length() < 3) continue;
                        freq.merge(token, 1, Integer::sum);
                    }
                }
            }
        }

        if (freq.isEmpty()) return ResponseEntity.ok(List.of());
        int lim = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<TermFrequency> out = freq.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(lim)
                .map(e -> new TermFrequency(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        log.info("WordCloud generado: docs={}, términos totales={}, devueltos={}", totalDocs, freq.size(), out.size());
        return ResponseEntity.ok(out);
    }

    private String normalizeKeyword(String kw) {
        if (kw == null) return "";
        String lower = kw.toLowerCase(Locale.ROOT);
        String norm = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        norm = norm.replaceAll("[^a-z0-9+ ]+", " ");
        norm = norm.replaceAll("\\s+", " ").trim();
        return norm;
    }
}

