# Requerimiento 2 — Algoritmos de similitud textual (clásicos e IA)

Este requerimiento implementa cuatro algoritmos clásicos de similitud y dos basados en IA para comparar abstracts. La app permite enviar dos o más artículos y recibir, por algoritmo, los pares más similares con métricas y tiempos.

## Endpoints y flujo
- Endpoint: `POST /api/algoritmos/similarity-analyze`
  - Entrada: lista de `ArticleDTO` (id, title, abstractText)
  - Salida: lista de `AlgorithmRunResult` (uno por algoritmo) con pares ordenados por score
- Internamente se construye un mapa `id -> abstract` y se ejecutan en paralelo los cálculos por algoritmo.

## Algoritmos clásicos implementados (4)
1) Levenshtein (distancia de edición)
   - Métrica: mínimo número de operaciones (insertar, eliminar, sustituir) para transformar A en B.
   - Similitud usada: `1 - d / max(|A|,|B|)` en [0..1].
   - Código principal:
```java
// LevenshteinSimilarity
int d = distance(a, b); // DP O(n*m)
double s = 1.0 - d / (double) Math.max(a.length(), b.length());
```

2) Jaccard (conjunto de tokens)
   - Tokenización simple (lower, split no-alfanumérico, tokens >=2).
   - Similitud: `|A ∩ B| / |A ∪ B|`.
```java
// JaccardSimilarity
Set<String> A = tokenize(a); Set<String> B = tokenize(b);
double s = intersection(A,B) / (double) union(A,B);
```

3) TF-IDF + Coseno
   - TF sublineal: `1 + log(tf)`; IDF suavizado; vectores L2.
   - Similitud: producto punto entre vectores normalizados.
```java
// TFIDFCosineSimilarity
Map<String,Integer> tf = termFreq(text);
double w = (1+log(tf))*idf;
// normalizar y coseno = dot(a,b)
```

4) BM25 simétrico documento-documento
   - Promedio BM25(A→B) y BM25(B→A) y normalización min-max a [0..1].
```java
// BM25Similarity
double s = (bm25(A->B) + bm25(B->A)) / 2;
// min-max en el run para normalizar
```

## Algoritmos con IA (2)
La base de código deja preparado el pipeline de preprocesamiento y vectorización (TF‑IDF) y es compatible con agregar modelos embebidos/externos:
- Embeddings (ej. Sentence-BERT): vectores densos por abstract y similitud coseno.
- Cross-encoder/biencoder: puntaje de similitud aprendida.

Puntos de integración sugeridos:
- Crear `EmbeddingSimilarityService` con un método `embed(List<String>)` y cache local.
- Agregar `AlgorithmRunResult` para `EMBEDDINGS_COSINE` y `CROSS_ENCODER`.
- Reutilizar la estructura de `AnalyzeSimilarityService` para orquestar y combinar resultados.

## Salida y metadatos
- Cada algoritmo retorna:
  - `algorithm`: nombre
  - `totalTimeMs`: tiempo total del run
  - `totalComparisons`: pares (n*(n-1)/2)
  - `results[]`: `{ idA, idB, score (0..1), distance (escala 0..1000), similarityPercent }`
- Los resultados se ordenan por `score` descendente.

## Fragmentos reales del proyecto
- Orquestación:
```java
// AnalyzeSimilarityService
algorithmRunResults.add(levenshteinSimilarity.levenshteinDistance(abstracts));
algorithmRunResults.add(jaccardSimilarity.jaccardDistance(abstracts));
algorithmRunResults.add(tfidfCosineSimilarity.tfidfCosineDistance(abstracts));
algorithmRunResults.add(bm25Similarity.bm25Distance(abstracts));
```

- TF‑IDF + Coseno (cálculo de vectores y producto punto):
```java
// TFIDFCosineSimilarity
double tfWeight = 1.0 + Math.log(tf);
double w = tfWeight * idf;
// ... normalización L2
double cos = dot(vecA, vecB); // [0,1]
```

- BM25 documento-documento (simétrico):
```java
// BM25Similarity
double sAB = bm25ScoreQueryOnDoc(terms(A), tf(B), lenB, avgDl, idf);
double sBA = bm25ScoreQueryOnDoc(terms(B), tf(A), lenA, avgDl, idf);
double s = (sAB + sBA)/2;
```

## Selección y comparación de múltiples artículos
- La app permite enviar 2 o más artículos; internamente se comparan todos los pares (i<j).
- El diseño es extensible: se pueden activar/desactivar algoritmos sin cambiar el contrato del endpoint.

## Notas
- Los métodos aplican normalización básica (minúsculas, separación no-alfanumérica, filtrado de tokens cortos).
- Para grandes volúmenes, se recomienda añadir muestreo o top‑K por cada documento para reducir costo O(n²).

