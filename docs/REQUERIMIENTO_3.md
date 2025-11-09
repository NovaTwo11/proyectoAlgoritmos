# Requerimiento 3 — Frecuencia de palabras dadas, descubrimiento de términos y precisión

Este requerimiento toma una categoría fija y sus 15 palabras asociadas, calcula su frecuencia en los abstracts, descubre hasta 15 términos nuevos relevantes y estima la precisión de esos nuevos términos.

## Endpoint y flujo
- Endpoint: `GET /api/algoritmos/keyword-analysis`
  - Entrada: sin parámetros (la categoría y las 15 palabras están “quemadas” en el servicio).
  - Salida: `KeywordAnalysisResponse` con:
    - `category`: nombre de la categoría
    - `givenKeywordFrequencies[]`: frecuencia por palabra dada (texto exacto, insensible a mayúsculas y acentos)
    - `discoveredKeywords[]`: hasta 15 términos nuevos con su frecuencia
    - `precision`: valor 0..1 (porcentaje de términos nuevos considerados relevantes)
    - `precisionExplanation`: texto breve del cálculo

## Palabras dadas (hardcoded)
Categoría: `Concepts of Generative AI in Education`
Palabras asociadas (15):
- Generative models, Prompting, Machine learning, Multimodality,
- Fine-tuning, Training data, Algorithmic bias, Explainability,
- Transparency, Ethics, Privacy, Personalization,
- Human-AI interaction, AI literacy, Co-creation

## Cómo se calcula (resumen)
1) Frecuencia de palabras dadas
   - Se normaliza cada abstract (lower, quitar acentos/símbolos; conservar '+').
   - Se cuentan ocurrencias exactas de cada palabra/frase dada dentro del texto normalizado.

2) Descubrimiento de hasta 15 términos
   - Se tokeniza cada abstract (regex por no‑alfanumérico), se filtran stopwords EN/ES y tokens cortos (≤2).
   - Se calcula TF‑IDF por token y se suman los pesos por término a través de documentos.
   - Se ordenan por TF‑IDF acumulado y se toman los top 15 que no estén en las palabras dadas.
   - Se reporta la frecuencia de aparición (conteo total) por término, para facilitar visualización.

3) Precisión de los términos nuevos
   - Se estima por co‑ocurrencia con palabras de la categoría: un término nuevo es relevante si aparece en abstracts que también contienen alguna palabra dada, en al menos el 10% de los artículos.
   - La precisión global es la fracción de términos nuevos que cumplen ese umbral.

## Fragmentos reales del proyecto
- Servicio principal:
```java
// KeywordAnalysisService.analyze(articles)
Map<String, Integer> givenFreq = countGivenKeywords(articles, GIVEN_KEYWORDS);
List<TermFrequency> discovered = discoverTopKeywords(articles, GIVEN_KEYWORDS, MAX_NEW_WORDS);
PrecisionResult precisionResult = calculateDetailedPrecision(discovered, GIVEN_KEYWORDS, articles);
```

- Normalización básica (casefolding + NFD sin acentos + limpieza):
```java
private String normalizeText(String s) {
    String lower = s.toLowerCase(Locale.ROOT);
    String norm = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return norm.replaceAll("[^a-z0-9+ ]+", " ").replaceAll("\\s+", " ").trim();
}
```

- Descubrimiento por TF‑IDF simple:
```java
// DF y TF por documento, y TF‑IDF acumulado
int N = docs.size();
Map<String, Integer> df = ...;
Map<String, Double> tfidf = new HashMap<>();
for (List<String> doc : docs) {
    Map<String, Long> tf = doc.stream().collect(groupingBy(s -> s, counting()));
    for (var e : tf.entrySet()) {
        double idf = Math.log((N + 1.0) / (df.getOrDefault(e.getKey(), 1)));
        tfidf.merge(e.getKey(), e.getValue() * idf, Double::sum);
    }
}
// top 15 excluyendo los dados
```

- Precisión por co‑ocurrencia:
```java
// relevante si co-ocurre con palabras dadas en >= 10% de artículos
boolean isRelevant = cooccurrenceCount >= Math.max(1, articles.size() * 0.1);
precision = relevantes / totalNuevos;
```

## Salida de ejemplo (estructura)
```json
{
  "category": "Concepts of Generative AI in Education",
  "givenKeywordFrequencies": [
    { "term": "Generative models", "frequency": 12 },
    { "term": "Prompting", "frequency": 8 },
    ...
  ],
  "discoveredKeywords": [
    { "term": "large", "frequency": 35 },
    { "term": "transformer", "frequency": 21 },
    ... (máx 15)
  ],
  "precision": 0.73,
  "precisionExplanation": "Precisión calculada como co-ocurrencia: ..."
}
```

## Notas
- Las palabras dadas y la categoría están “quemadas” en `KeywordAnalysisService`.
- Las stopwords EN/ES son básicas y pueden ampliarse.
- Si un abstract queda vacío tras limpieza se descarta, y la deduplicación previa por título evita dobles conteos.

