package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.KeywordAnalysisResponse;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.TermFrequency;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KeywordAnalysisService {

    // Categoría y palabras asociadas "quemadas" (hardcoded)
    private static final String CATEGORY = "Concepts of Generative AI in Education";
    private static final List<String> GIVEN_KEYWORDS = Arrays.asList(
            "Generative models", "Prompting", "Machine learning", "Multimodality",
            "Fine-tuning", "Training data", "Algorithmic bias", "Explainability",
            "Transparency", "Ethics", "Privacy", "Personalization",
            "Human-AI interaction", "AI literacy", "Co-creation"
    );
    private static final int MAX_NEW_WORDS = 15;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // Español + inglés básico, se pueden ampliar
            "a","ante","bajo","cabe","con","contra","de","desde","en","entre","hacia","hasta","para","por","segun","sin","so","sobre","tras",
            "el","la","los","las","un","una","unos","unas","y","o","u","e","que","como","se","su","sus","es","son","fue","fueron","ser","esta","está","están","estamos","estudio","paper","study","the","and","or","of","to","in","on","for","with","by","an","as","at","from","this","that","these","those","we","our","their","it","its","be","are","is","was","were","have","has","had","can","could","may","might","into","across","using","use","used","via","results","result","show","shows","find","finds","analysis","method","methods","data","based","approach","approaches","model","models","paper","study"
    ));

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9+]+");

    public ResponseEntity<KeywordAnalysisResponse> analyze(List<ArticleDTO> articles) {

        // 1) Frecuencia de palabras asociadas (búsqueda literal insensible a mayúsculas/acentos)
        Map<String, Integer> givenFreq = countGivenKeywords(articles, GIVEN_KEYWORDS);
        List<TermFrequency> givenTF = toSortedTF(givenFreq);

        // 2) Extraer nuevas palabras: usar TF-IDF simple por tokens, filtrar stop words y tokens cortos
        List<TermFrequency> discovered = discoverTopKeywords(articles, GIVEN_KEYWORDS, MAX_NEW_WORDS);

        // 3) Estimar precisión: porcentaje de nuevas que co-ocurren con palabras de la categoría en abstracts
        PrecisionResult precisionResult = calculateDetailedPrecision(discovered, GIVEN_KEYWORDS, articles);
        double precision = precisionResult.getOverallPrecision();

        String explanation = String.format(Locale.ROOT,
                "Precisión calculada como co-ocurrencia: %d de %d términos nuevos (%.0f%%) aparecen en abstracts que contienen alguna palabra de la categoría en al menos el 10%% de los artículos.",
                precisionResult.getRelevantCount(), discovered.size(), precision * 100.0);

        KeywordAnalysisResponse resp = KeywordAnalysisResponse.builder()
                .category(CATEGORY)
                .givenKeywordFrequencies(givenTF)
                .discoveredKeywords(discovered)
                .precision(precision)
                .precisionExplanation(explanation)
                .build();

        return ResponseEntity.ok(resp);
    }

    // --- Paso 1: frecuencia de las palabras/frases dadas ---
    private Map<String, Integer> countGivenKeywords(List<ArticleDTO> articles, List<String> givenKeywords) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String kw : givenKeywords) freq.put(kw, 0);

        for (ArticleDTO a : articles) {
            String abs = normalizeText(a.getAbstractText());
            if (abs.isBlank()) continue;
            for (String kw : givenKeywords) {
                String pat = normalizeText(kw);
                if (pat.isBlank()) continue;
                int count = countOccurrences(abs, pat);
                if (count > 0) freq.put(kw, freq.getOrDefault(kw, 0) + count);
            }
        }
        return freq;
    }

    private int countOccurrences(String text, String needle) {
        int c = 0, idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            c++; idx += needle.length();
        }
        return c;
    }

    // --- Paso 2: descubrimiento de términos con TF-IDF simple ---
    private List<TermFrequency> discoverTopKeywords(List<ArticleDTO> articles, List<String> givenKeywords, int maxNew) {
        // Preparar documentos tokenizados
        List<List<String>> docs = new ArrayList<>();
        for (ArticleDTO a : articles) {
            String abs = normalizeText(a.getAbstractText());
            if (abs.isBlank()) continue;
            List<String> tokens = Arrays.stream(TOKEN_SPLIT.split(abs))
                    .filter(t -> !t.isBlank())
                    .filter(t -> t.length() > 2)
                    .filter(t -> !STOP_WORDS.contains(t))
                    .toList();
            docs.add(tokens);
        }
        if (docs.isEmpty()) return List.of();

        // DF (document frequency)
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : docs) {
            Set<String> unique = new HashSet<>(doc);
            for (String t : unique) df.merge(t, 1, Integer::sum);
        }
        int N = docs.size();

        // TF-IDF acumulado (suma TF-IDF por documento)
        Map<String, Double> tfidf = new HashMap<>();
        Map<String, Integer> totalCounts = new HashMap<>();
        for (List<String> doc : docs) {
            Map<String, Long> tf = doc.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            for (Map.Entry<String, Long> e : tf.entrySet()) {
                String term = e.getKey();
                double tfVal = e.getValue();
                double idf = Math.log((N + 1.0) / (df.getOrDefault(term, 1)));
                tfidf.merge(term, tfVal * idf, Double::sum);
                totalCounts.merge(term, e.getValue().intValue(), Integer::sum);
            }
        }

        // Excluir términos que ya están en las palabras dadas (normalizados)
        Set<String> givenNorm = givenKeywords.stream().map(this::normalizeText).collect(Collectors.toSet());

        // Ord. por TF-IDF desc y tomar top
        List<String> topTerms = tfidf.entrySet().stream()
                .filter(e -> !givenNorm.contains(e.getKey()))
                .sorted((a,b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(maxNew)
                .map(Map.Entry::getKey)
                .toList();

        // Devolver con el conteo total (no TF-IDF) para presentar "frecuencia de aparición"
        List<TermFrequency> out = new ArrayList<>();
        for (String t : topTerms) {
            out.add(new TermFrequency(t, totalCounts.getOrDefault(t, 0)));
        }
        return out;
    }

    // --- Paso 3: precisión mejorada con métricas detalladas ---
    private PrecisionResult calculateDetailedPrecision(List<TermFrequency> discovered,
                                                       List<String> givenKeywords,
                                                       List<ArticleDTO> articles) {
        PrecisionResult result = new PrecisionResult();

        if (discovered.isEmpty()) {
            result.setOverallPrecision(0.0);
            return result;
        }

        List<TermPrecision> termPrecisions = new ArrayList<>();
        int relevantCount = 0;

        for (TermFrequency termFreq : discovered) {
            String term = termFreq.getTerm();
            TermPrecision termPrecision = new TermPrecision();
            termPrecision.setTerm(term);
            termPrecision.setFrequency(termFreq.getFrequency());

            // Calcular co-ocurrencia detallada
            long cooccurrenceCount = calculateExactCooccurrence(term, givenKeywords, articles);
            long totalWithTerm = calculateTotalWithTerm(term, articles);

            termPrecision.setCooccurrenceCount(cooccurrenceCount);
            termPrecision.setTotalWithTerm(totalWithTerm);

            double cooccurrenceRatio = totalWithTerm > 0 ?
                    (double) cooccurrenceCount / totalWithTerm : 0.0;
            termPrecision.setCooccurrenceRatio(cooccurrenceRatio);

            // Umbral: debe co-ocurrir en al menos 10% de los artículos
            boolean isRelevant = cooccurrenceCount >= Math.max(1, articles.size() * 0.1);
            termPrecision.setRelevant(isRelevant);

            if (isRelevant) {
                relevantCount++;
            }

            termPrecisions.add(termPrecision);
        }

        result.setTermPrecisions(termPrecisions);
        result.setRelevantCount(relevantCount);
        result.setTotalTerms(discovered.size());
        result.setOverallPrecision(relevantCount / (double) discovered.size());

        return result;
    }

    // --- Métodos de cálculo auxiliares para precisión ---
    private long calculateExactCooccurrence(String term, List<String> givenKeywords, List<ArticleDTO> articles) {
        String t = normalizeText(term);
        return articles.stream()
                .filter(article ->
                        containsTerm(article.getAbstractText(), t) &&
                                containsAnyKeyword(article.getAbstractText(), givenKeywords)
                )
                .count();
    }

    private long calculateTotalWithTerm(String term, List<ArticleDTO> articles) {
        String t = normalizeText(term);
        return articles.stream()
                .filter(article -> containsTerm(article.getAbstractText(), t))
                .count();
    }

    private boolean containsTerm(String text, String term) {
        if (text == null || term == null) return false;
        return normalizeText(text).contains(term);
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        String textNorm = normalizeText(text);
        return keywords.stream()
                .filter(keyword -> keyword != null)
                .anyMatch(keyword -> textNorm.contains(normalizeText(keyword)));
    }


    private boolean coOccursWithGiven(String term, List<String> givenKeywords, List<ArticleDTO> articles) {
        // Umbral: término debe co-ocurrir en al menos 10% de los artículos
        int cooccurrenceThreshold = Math.max(1, (int) (articles.size() * 0.1));

        long cooccurrenceCount = calculateExactCooccurrence(term, givenKeywords, articles);
        return cooccurrenceCount >= cooccurrenceThreshold;
    }

    // --- utilidades de normalización ---
    private String normalizeText(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        String norm = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // quitar acentos
        // Mantener signos + (p. ej., c++), y alfanum.
        return norm.replaceAll("[^a-z0-9+ ]+", " ").replaceAll("\\s+", " ").trim();
    }

    private List<TermFrequency> toSortedTF(Map<String, Integer> freq) {
        return freq.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new TermFrequency(e.getKey(), e.getValue()))
                .toList();
    }

    // --- Clases de apoyo para resultados detallados ---
    public static class PrecisionResult {
        private double overallPrecision;
        private int relevantCount;
        private int totalTerms;
        private List<TermPrecision> termPrecisions;

        public double getOverallPrecision() { return overallPrecision; }
        public void setOverallPrecision(double overallPrecision) { this.overallPrecision = overallPrecision; }

        public int getRelevantCount() { return relevantCount; }
        public void setRelevantCount(int relevantCount) { this.relevantCount = relevantCount; }

        public int getTotalTerms() { return totalTerms; }
        public void setTotalTerms(int totalTerms) { this.totalTerms = totalTerms; }

        public List<TermPrecision> getTermPrecisions() { return termPrecisions; }
        public void setTermPrecisions(List<TermPrecision> termPrecisions) { this.termPrecisions = termPrecisions; }
    }

    public static class TermPrecision {
        private String term;
        private int frequency;
        private long cooccurrenceCount;
        private long totalWithTerm;
        private double cooccurrenceRatio;
        private boolean relevant;

        public String getTerm() { return term; }
        public void setTerm(String term) { this.term = term; }

        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }

        public long getCooccurrenceCount() { return cooccurrenceCount; }
        public void setCooccurrenceCount(long cooccurrenceCount) { this.cooccurrenceCount = cooccurrenceCount; }

        public long getTotalWithTerm() { return totalWithTerm; }
        public void setTotalWithTerm(long totalWithTerm) { this.totalWithTerm = totalWithTerm; }

        public double getCooccurrenceRatio() { return cooccurrenceRatio; }
        public void setCooccurrenceRatio(double cooccurrenceRatio) { this.cooccurrenceRatio = cooccurrenceRatio; }

        public boolean isRelevant() { return relevant; }
        public void setRelevant(boolean relevant) { this.relevant = relevant; }
    }
}