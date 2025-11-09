# Requerimiento 4 — Clustering jerárquico y dendrogramas (single/complete/average)

Este requerimiento implementa el pipeline completo para agrupar abstracts y representar la similitud mediante dendrogramas usando tres algoritmos de enlace: single, complete y average.

## Endpoint y flujo
- Endpoint: `GET /api/algoritmos/dendrograma`
  - Entrada: sin parámetros (toma los artículos del Req. 1 ya unificados)
  - Salida: JSON con 3 imágenes en base64 y ruta de archivo por algoritmo:
    - `single`, `complete`, `average`
  - Las imágenes se guardan en `src/main/resources/data/dendrogramas` con prefijos `dendro_single`, `dendro_complete`, `dendro_average`.

## Pipeline implementado
1) Preprocesamiento
   - Normalización Unicode, minúsculas, eliminación de acentos y símbolos.
   - Tokenización por regex y filtrado de stopwords EN/ES; longitud mínima de token: 3.
   - (Opcional) bigramas desactivados por defecto.

2) Vectorización y similitud
   - TF‑IDF con tf sublineal `1 + log(tf)` e idf suavizado.
   - Normalización L2 de vectores; similitud coseno y distancias euclidianas derivadas.

3) Clustering jerárquico
   - Se usa `HierarchicalClusteringCore` con `Linkage`: `SINGLE`, `COMPLETE`, `AVERAGE`.
   - Se parte de N clusters unitarios y se realizan N−1 merges, guardando la altura (distancia) de cada fusión.

4) Render del dendrograma
   - `DendrogramRendererService` convierte la secuencia de merges y etiquetas en una imagen PNG (base64 y archivo).
   - Directorio de salida configurable (default: `src/main/resources/data/dendrogramas`).

## Fragmentos reales del proyecto
- Orquestación del R4:
```java
// Requirement4OrchestratorService.run(articles)
var pre = preprocessingPipelineService.preprocessArticles(articles).getBody();
SimilarityResponse sim = similarityService.computeTfidfCosineAndEuclidean(pre.getArticles());
double[][] d = sim.getDistancesEuclidean();
var single = hclust.agglomerate(d, Linkage.SINGLE);
var complete = hclust.agglomerate(d, Linkage.COMPLETE);
var average = hclust.agglomerate(d, Linkage.AVERAGE);
var rSingle = renderer.renderAndSaveBase64(single, labels, outDir, "dendro_single");
```

- Cálculo TF‑IDF + coseno y distancias:
```java
// SimilarityService
idf.put(term, Math.log((n + 1.0) / (dfi + 1.0)) + 1.0);
double w = (1.0 + Math.log(tf)) * idf(term);
// normalización L2 y:
double cos = dot(vecA, vecB);
```

- Núcleo de clustering jerárquico:
```java
// HierarchicalClusteringCore.agglomerate(dist, linkage)
// SINGLE: min, COMPLETE: max, AVERAGE: promedio ponderado por tamaños (UPGMA)
merges.add(new Merge(ai, bi, bestDistance, size[ai]+size[bi]));
```

## Criterio de coherencia
- La coherencia de los agrupamientos se evalúa comparando visualmente y por métricas simples (opcional):
  - Alturas de fusión: enlaces que conservan estructuras compactas (menores alturas promedio) pueden ser más coherentes para datos densos.
  - Estabilidad: consistencia de clusters frente a pequeñas variaciones (no implementado, sugerencia futura).
  - Para abstracts con sinónimos/variantes, `AVERAGE` suele dar agrupamientos más equilibrados que `SINGLE` (susceptible a "efecto cadena") o `COMPLETE` (tiende a clusters compactos y separados).

## Rutas y limpieza
- Antes de ejecutar, el orquestador limpia `src/main/resources/data/dendrogramas`.
- Los nombres de salida incluyen timestamp para evitar colisiones.

## Notas
- Los artículos se leen de `resultados_unificados.bib` (Req. 1).
- Si no hay abstracts válidos tras preprocesamiento, el endpoint retorna un mensaje informativo.

