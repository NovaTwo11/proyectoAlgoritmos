# Requerimiento 1 — Automatización de búsqueda, descarga y unificación (ACM Digital Library y Web of Science)

Este requerimiento automatiza la búsqueda en dos bases (ACM Digital Library y Web of Science), descarga las citas en formato BibTeX, normaliza los registros y genera dos salidas: un archivo unificado de artículos únicos y otro con los duplicados detectados.

## Qué hace (visión corta)
- Dispara la descarga desde un endpoint REST con una `query` opcional.
- Limpia directorios de trabajo si la query cambió (para evitar mezclar resultados viejos).
- Abre un navegador (Selenium/Chrome), busca en ACM y Web of Science, exporta BibTeX y guarda en `src/main/resources/downloads`.
- Parseo + normalización con jbibtex (y fallback robusto), deduplicación y fusión de campos.
- Escribe salidas:
  - Únicos: `src/main/resources/data/output/resultados_unificados.bib`
  - Duplicados: `src/main/resources/data/output/resultados_duplicados.bib`
  - Normalizados por fuente: `src/main/resources/data/normalized/*.bib`

## Punto de entrada (API)
- Endpoint real del proyecto: `POST /api/algoritmos/download-articles`
- Parámetro: `query` (opcional). Si se omite, usa `automation.search.query` desde `application.yml`.

Ejemplo (bash):
```bash
curl -X POST "http://localhost:8080/api/algoritmos/download-articles?query=generative%20artificial%20intelligence"
```

## Flujo implementado (resumen)
1) Verifica si la `query` es igual a la última (`src/main/resources/data/last_query.txt`). Si cambió, limpia:
   - `src/main/resources/downloads`, `src/main/resources/data/normalized`, `src/main/resources/data/output` y `src/main/resources/data/dendrogramas`.
2) Descarga con Selenium:
   - ACM y Web of Science: ejecuta búsqueda, exporta BibTeX y guarda en `downloads` con prefijo de fuente + timestamp.
3) Unificación automática:
   - Lee todos los `.bib`, parsea a objetos `Article`, normaliza campos y deduplica.
   - Si hay duplicados (mismo título+autores normalizados), fusiona campos (autores/keywords sin repetir, `abstract` más largo, campos no vacíos).
4) Exporta resultados:
   - `resultados_unificados.bib` (únicos) y `resultados_duplicados.bib` (duplicados) en `src/main/resources/data/output`.
   - Además, escribe `acm_normalized.bib` y `webofscience_normalized.bib` en `src/main/resources/data/normalized`.

## Código principal (extractos)
- Controlador (punto de entrada):
```java
// AutomationController
@PostMapping("/download-articles")
public ResponseEntity<String> runReq1(@RequestParam(required = false) String query) {
    return orchestrator.downloadArticles(query);
}
```

- Orquestador (descarga + limpieza + unificación):
```java
// AutomationOrchestratorService
public ResponseEntity<String> downloadArticles(String query) {
    String effectiveQuery = (query != null && !query.isBlank()) ? query : cfg.getSearchQuery();
    String last = readLastQuery();
    boolean sameQuery = last != null && last.equalsIgnoreCase(effectiveQuery);

    if (!sameQuery) {
        cleanDir("src/main/resources/data/normalized");
        cleanDir("src/main/resources/data/output");
        cleanDir(cfg.getDownloadDirectory());
        cleanDir(dendroOutDir);
        writeLastQuery(effectiveQuery);
    }

    if (!sameQuery || isEmptyDir(cfg.getDownloadDirectory())) {
        WebDriver driver = driverFactory.createChromeDriver();
        try {
            acm.download(driver, effectiveQuery);
            wos.download(driver, effectiveQuery);
        } finally { driver.quit(); }
    }
    downloadAll(); // parsear, unificar y exportar
    return ResponseEntity.ok("Proceso completado exitosamente.");
}

public void downloadAll() {
    String outDir = "src/main/resources/data/output";
    String unified = outDir + "/resultados_unificados.bib";
    String dups = outDir + "/resultados_duplicados.bib";
    unifier.processDownloaded(cfg.getDownloadDirectory(), unified, dups);
}
```

- Unificación (clave por título+autores normalizados y fusión de campos):
```java
// BibTeXUnificationService
public Map<String, List<Article>> unify(List<Article> input) {
    Map<String, Article> unique = new LinkedHashMap<>();
    List<Article> dups = new ArrayList<>();
    for (Article a : input) {
        String key = titleKey(a.getTitle()) + "|" + authorsKey(a.getAuthors());
        if (!unique.containsKey(key)) {
            unique.put(key, a);
        } else {
            Article merged = merge(unique.get(key), a);
            unique.put(key, merged);
            dups.add(a);
        }
    }
    return Map.of("unified", new ArrayList<>(unique.values()),
                  "duplicates", dups);
}
```

- Exportación de resultados:
```java
// BibTeXParserService (resumen)
public void exportCustomBib(List<Article> arts, File out) throws IOException {
    out.getParentFile().mkdirs();
    try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
        for (Article a : arts) {
            w.write(formatAsBib(a));
            w.newLine();
        }
    }
}
```

## Dónde quedan los archivos
- Descargas (crudas): `src/main/resources/downloads/`
- Normalizados por fuente: `src/main/resources/data/normalized/`
- Unificados y duplicados: `src/main/resources/data/output/`

## Notas
- El proceso es end‑to‑end: desde la búsqueda hasta la generación de los dos archivos finales.
- La deduplicación favorece campos no vacíos y conserva el resumen más largo para maximizar información en el registro unificado.
- Los logs de progreso/errores se guardan en `logs/application.log`.
