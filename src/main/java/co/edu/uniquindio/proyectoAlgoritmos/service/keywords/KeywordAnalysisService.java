package co.edu.uniquindio.proyectoAlgoritmos.service.keywords;

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

    // Categoría y palabras asociadas (hardcoded según el requerimiento)
    private static final String CATEGORY = "Concepts of Generative AI in Education";
    private static final List<String> GIVEN_KEYWORDS = Arrays.asList(
            "Generative models", "Prompting", "Machine learning", "Multimodality",
            "Fine-tuning", "Training data", "Algorithmic bias", "Explainability",
            "Transparency", "Ethics", "Privacy", "Personalization",
            "Human-AI interaction", "AI literacy", "Co-creation"
    );

    private static final int MAX_NEW_WORDS = 15;

    // Stopwords mínimas ES/EN (amplía según tu corpus)
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // Español
            "a","ante","bajo","cabe","con","contra","de","desde","en","entre","hacia","hasta","para","por","segun","sin","so","sobre","tras",
            "el","la","los","las","un","una","unos","unas","y","o","u","e","que","como","se","su","sus","es","son","fue","fueron","ser","esta","está","están","estamos",
            // Inglés y términos genéricos de papers
            "the","and","or","of","to","in","on","for","with","by","an","as","at","from","this","that","these","those","we","our","their",
            "it","its","be","are","is","was","were","have","has","had","can","could","may","might",
            "into","across","using","use","used","via","results","result","show","shows","find","finds",
            "analysis","method","methods","data","based","approach","approaches","model","models","paper","study"
    ));

    // Separador léxico: todo lo que NO sea [a-z0-9+]
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9+]+");

    public ResponseEntity<KeywordAnalysisResponse> analyze(List<ArticleDTO> articles) {

        // 1) Frecuencia de palabras/frases dadas (coincidencia por término/frase, sin subcadenas)
        Map<String, Integer> givenFreq = countGivenKeywords(articles, GIVEN_KEYWORDS);
        List<TermFrequency> givenTF = toSortedTF(givenFreq);

        // 2) Descubrir nuevas palabras/frases con TF-IDF sobre uni/bi/tri-gramas
        List<TermFrequency> discovered = discoverTopKeywords(articles, GIVEN_KEYWORDS, MAX_NEW_WORDS);

        // 3) Estimar precisión por co-ocurrencia (≥10% de los artículos)
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

    // =========================== Paso 1: Frecuencia de keywords dadas ===========================

    private Map<String, Integer> countGivenKeywords(List<ArticleDTO> articles, List<String> givenKeywords) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String kw : givenKeywords) freq.put(kw, 0);

        for (ArticleDTO a : articles) {
            String abs = normalizeText(a.getAbstractText());
            if (abs.isBlank()) continue;
            for (String kw : givenKeywords) {
                int count = countOccurrences(abs, normalizeText(kw));
                if (count > 0) freq.put(kw, freq.getOrDefault(kw, 0) + count);
            }
        }
        return freq;
    }

    /**
     * Cuenta ocurrencias de una aguja (ya normalizada) en un texto normalizado,
     * asegurando bordes léxicos: inicio/fin o separado por no [a-z0-9+].
     */
    private int countOccurrences(String normalizedText, String normalizedNeedle) {
        if (normalizedText == null || normalizedNeedle == null || normalizedNeedle.isBlank()) return 0;
        String n = Pattern.quote(normalizedNeedle);
        // Bordes léxicos: lookbehind/lookahead para ^ o separador, y $ o separador
        Pattern p = Pattern.compile("(?:(?<=^)|(?<=[^a-z0-9+]))" + n + "(?:(?=$)|(?=[^a-z0-9+]))");
        var m = p.matcher(normalizedText);
        int c = 0;
        while (m.find()) c++;
        return c;
    }

    // =========================== Paso 2: Descubrimiento TF-IDF (uni/bi/tri) ===========================

    private List<TermFrequency> discoverTopKeywords(List<ArticleDTO> articles, List<String> givenKeywords, int maxNew) {
        // Preparar documentos (lista de términos por doc): tokens (unigrama) -> generar bigramas y trigramas
        List<List<String>> docsTerms = new ArrayList<>();
        for (ArticleDTO a : articles) {
            String abs = normalizeText(a.getAbstractText());
            if (abs.isBlank()) continue;

            // Unigrama (filtra stopwords y tokens cortos)
            List<String> tokens = Arrays.stream(TOKEN_SPLIT.split(abs))
                    .filter(t -> !t.isBlank())
                    .filter(t -> t.length() > 2)
                    .filter(t -> !STOP_WORDS.contains(t))
                    .toList();

            if (tokens.isEmpty()) continue;

            // Agregar uni + bi + tri (bi/tri se forman desde tokens ya filtrados)
            List<String> terms = toNgrams(tokens);
            docsTerms.add(terms);
        }
        if (docsTerms.isEmpty()) return List.of();

        // DF por término
        Map<String, Integer> df = new HashMap<>();
        for (List<String> terms : docsTerms) {
            Set<String> unique = new HashSet<>(terms);
            for (String t : unique) df.merge(t, 1, Integer::sum);
        }
        int N = docsTerms.size();

        // TF-IDF acumulado (suma por documento de log1p(tf) * idf_suavizado)
        Map<String, Double> tfidf = new HashMap<>();
        Map<String, Integer> totalCounts = new HashMap<>(); // frecuencia total de aparición (para mostrar)
        for (List<String> terms : docsTerms) {
            Map<String, Long> tf = terms.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            for (Map.Entry<String, Long> e : tf.entrySet()) {
                String term = e.getKey();
                int dfi = Math.max(1, df.getOrDefault(term, 1));
                double idf = Math.log((N + 1.0) / (dfi + 1.0)) + 1.0; // idf estable
                double tfWeight = Math.log1p(e.getValue());           // tf sublineal
                tfidf.merge(term, tfWeight * idf, Double::sum);
                totalCounts.merge(term, e.getValue().intValue(), Integer::sum);
            }
        }

        // Excluir términos que ya están dados (normalizados)
        Set<String> givenNorm = givenKeywords.stream().map(this::normalizeText).collect(Collectors.toSet());

        // Top-K por TF-IDF descendente
        List<String> topTerms = tfidf.entrySet().stream()
                .filter(e -> !givenNorm.contains(e.getKey()))
                .sorted((a,b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(maxNew)
                .map(Map.Entry::getKey)
                .toList();

        // Empaquetar usando la frecuencia total (no TF-IDF) para mostrar “frecuencia de aparición”
        List<TermFrequency> out = new ArrayList<>();
        for (String t : topTerms) {
            out.add(new TermFrequency(t, totalCounts.getOrDefault(t, 0)));
        }
        return out;
    }

    private List<String> toNgrams(List<String> toks) {
        List<String> out = new ArrayList<>(toks.size() * 3);
        // unigrama
        out.addAll(toks);
        // bigrama
        for (int i = 0; i + 1 < toks.size(); i++) {
            out.add(toks.get(i) + " " + toks.get(i + 1));
        }
        // trigram
        for (int i = 0; i + 2 < toks.size(); i++) {
            out.add(toks.get(i) + " " + toks.get(i + 1) + " " + toks.get(i + 2));
        }
        return out;
    }

    // =========================== Paso 3: Precisión por co-ocurrencia ===========================

    private PrecisionResult calculateDetailedPrecision(List<TermFrequency> discovered,
                                                       List<String> givenKeywords,
                                                       List<ArticleDTO> articles) {
        PrecisionResult result = new PrecisionResult();

        if (discovered.isEmpty()) {
            result.setOverallPrecision(0.0);
            result.setRelevantCount(0);
            result.setTotalTerms(0);
            result.setTermPrecisions(Collections.emptyList());
            return result;
        }

        List<TermPrecision> termPrecisions = new ArrayList<>();
        int relevantCount = 0;
        int threshold = Math.max(1, (int) Math.ceil(articles.size() * 0.10)); // 10%

        for (TermFrequency termFreq : discovered) {
            String term = termFreq.getTerm();
            TermPrecision tp = new TermPrecision();
            tp.setTerm(term);
            tp.setFrequency(termFreq.getFrequency());

            long totalWithTerm = calculateTotalWithTerm(term, articles);
            long cooccurrenceCount = calculateExactCooccurrence(term, givenKeywords, articles);

            tp.setTotalWithTerm(totalWithTerm);
            tp.setCooccurrenceCount(cooccurrenceCount);

            double ratio = (totalWithTerm > 0) ? (cooccurrenceCount / (double) totalWithTerm) : 0.0;
            // redondeo a 4 decimales para mantener precisión sin ruido visual
            tp.setCooccurrenceRatio(Math.round(ratio * 10000.0) / 10000.0);

            boolean isRelevant = cooccurrenceCount >= threshold;
            tp.setRelevant(isRelevant);
            if (isRelevant) relevantCount++;

            termPrecisions.add(tp);
        }

        result.setTermPrecisions(termPrecisions);
        result.setRelevantCount(relevantCount);
        result.setTotalTerms(discovered.size());
        result.setOverallPrecision(discovered.isEmpty() ? 0.0 : relevantCount / (double) discovered.size());

        return result;
    }

    // ------- Auxiliares de precisión (con bordes léxicos) -------

    private long calculateExactCooccurrence(String term, List<String> givenKeywords, List<ArticleDTO> articles) {
        String termNorm = normalizeText(term);
        return articles.stream()
                .filter(a -> {
                    String abs = normalizeText(a.getAbstractText());
                    return containsTerm(abs, termNorm) && containsAnyKeyword(abs, givenKeywords);
                })
                .count();
    }

    private long calculateTotalWithTerm(String term, List<ArticleDTO> articles) {
        String termNorm = normalizeText(term);
        return articles.stream()
                .filter(a -> containsTerm(normalizeText(a.getAbstractText()), termNorm))
                .count();
    }

    private boolean containsAnyKeyword(String normalizedText, List<String> keywords) {
        if (normalizedText == null || keywords == null) return false;
        for (String kw : keywords) {
            String k = normalizeText(kw);
            if (!k.isBlank() && containsTerm(normalizedText, k)) return true;
        }
        return false;
    }

    /**
     * Verifica presencia de término/frase normalizada respetando bordes léxicos.
     */
    private boolean containsTerm(String normalizedText, String normalizedNeedle) {
        if (normalizedText == null || normalizedNeedle == null || normalizedNeedle.isBlank()) return false;
        String n = Pattern.quote(normalizedNeedle);
        Pattern p = Pattern.compile("(?:(?<=^)|(?<=[^a-z0-9+]))" + n + "(?:(?=$)|(?=[^a-z0-9+]))");
        return p.matcher(normalizedText).find();
    }

    // =========================== Utilidades comunes ===========================

    /**
     * Normaliza a minúsculas, NFD (quita diacríticos),
     * conserva letras, dígitos, espacios y '+' (p.ej., c++).
     */
    private String normalizeText(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        String norm = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // quitar acentos
        // Mantener '+' y espacio; convertir lo demás a espacio y colapsar
        return norm.replaceAll("[^a-z0-9+ ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<TermFrequency> toSortedTF(Map<String, Integer> freq) {
        return freq.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new TermFrequency(e.getKey(), e.getValue()))
                .toList();
    }

    // =========================== Clases de apoyo ===========================

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
        private int frequency;              // frecuencia total (suma en todos los docs)
        private long cooccurrenceCount;     // docs con término + alguna keyword dada
        private long totalWithTerm;         // docs con el término
        private double cooccurrenceRatio;   // cooccurrenceCount / totalWithTerm
        private boolean relevant;           // co-ocurre en ≥ 10% de los artículos

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
