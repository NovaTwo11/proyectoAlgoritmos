# Requerimiento 1 — Automatización de búsqueda y descarga bibliográfica (ScienceDirect e IEEE Xplore)

Este requerimiento automatiza la búsqueda de artículos en ScienceDirect e IEEE Xplore, descarga las citas en formato BibTeX, normaliza los registros y genera salidas unificadas, filtradas y duplicadas según el flujo definido en Automatización.

## Objetivo
- Ejecutar una búsqueda por cadena (query) en ScienceDirect e IEEE Xplore.
- Exportar los resultados en BibTeX (100 por página cuando sea posible) y paginar hasta un máximo configurable.
- Leer todos los .bib descargados, parsearlos, normalizar campos y hacer deduplicación por DOI o título normalizado + año.
- Escribir salidas en rutas claras por fuente y en archivos de resultados unificados/filtrados/duplicados.

## Alcance y supuestos
- Bases implementadas: ScienceDirect y IEEE Xplore.
- Autenticación: vía proxy institucional/SSO y/o botón “Continuar con Google” (id="btn-google"). Si el flujo requiere 2FA, se permite intervención manual.
- El scraping usa Selenium (Chrome) y espera la descarga del .bib para renombrarlo con prefijo de la fuente + timestamp.

## Flujo de alto nivel (Automatización)
1) Autenticación vía proxy institucional:
   - IEEE: https://login.crai.referencistas.com/login?url=https://ieeexplore.ieee.org
   - ScienceDirect: https://login.crai.referencistas.com/login?url=https://www.sciencedirect.com
   - Si no se logra, se intenta navegar desde el portal institucional configurado y, en último caso, acceder directo a la base.
   - Google SSO: si aparece el botón con `id="btn-google"`, se hace clic y se completa el flujo (con credenciales configuradas o manualmente si hay 2FA).

2) Búsqueda:
   - Escribir la cadena `query` en el buscador de la base y enviar.

3) Resultados por página:
   - Intentar configurar 100 por página mediante los controles disponibles (dropdown/select), si la UI lo permite.

4) Exportación por página:
   - Seleccionar todos los resultados visibles (si hay opción) y abrir el diálogo de exportación.
   - Elegir formato BibTeX y descargar.
   - Esperar finalización de la descarga y renombrar el .bib a `Fuente_yyyyMMdd_HHmmss_SSS.bib` en `./resources/downloads`.

5) Paginación:
   - Ir a la siguiente página y repetir hasta alcanzar el límite `max pages` o hasta que no haya más páginas.

6) Post-proceso:
   - Leer todos los .bib descargados, parsear con jbibtex, normalizar campos y deduplicar.
   - Generar salidas unificadas y listados de filtrados/duplicados en `./resources/data/output/douwnloads`.

## Punto de entrada (API)
- Endpoint: `POST /api/automation/requirement-1`
- Parámetro: `query` (opcional). Si no se envía, se usa el valor por defecto de configuración.

Ejemplo (Windows CMD):
```
curl -X POST "http://localhost:8080/api/automation/requirement-1?query=generative%20artificial%20intelligence"
```

## Parámetros y configuración
- En `src/main/resources/application.yml` (claves relevantes):
  - `automation.email` y `automation.password`: credenciales (correo académico/Google, según flujo). Puedes usar variables de entorno si lo prefieres:
    - `automation.email: "${ACADEMIC_EMAIL:}"`
    - `automation.password: "${MAIL_PASSWORD:}"`
  - `automation.search.query`: query por defecto cuando el endpoint no recibe `query`.
  - `automation.download.directory`: ruta donde el navegador guarda los .bib. Por defecto: `./resources/downloads`.
  - `automation.portal.url`: URL del portal institucional para navegar a las bases si el proxy no redirige automáticamente.
  - `automation.max.pages`: número máximo de páginas de resultados a procesar por base. Ej.: `3`.
  - `automation.headless`: `false` recomendado para poder intervenir manualmente en SSO/2FA si fuese necesario.

Sobre “pages”: controla cuántas páginas de resultados se recorren. Si la búsqueda devuelve 24 páginas y `automation.max.pages = 3`, solo se procesan las 3 primeras. Si quieres intentar procesar “todas”, puedes subir este número, aunque la UI o límites del sitio pueden acotar.

## Detalles de UI — IEEE Xplore (100 por página y exportación)
Para robustecer la automatización en IEEE Xplore, estos son los elementos relevantes reportados:
- Cambiar a 100 por página:
  - Botón principal: un botón con texto `Items Per Page` que abre el panel de opciones.
  - Opciones disponibles dentro del panel: botones con textos `10`, `25`, `50`, `75`, `100`. Seleccionar `100`.
  - En algunas vistas, puede existir un botón adicional con texto `100` fuera del panel; priorizar el panel de opciones si ambos aparecen.
- Seleccionar todos los resultados en la página:
  - Checkbox de selección global: `<input>` (checkbox de “seleccionar todos” en la grilla de resultados).
- Exportar:
  - Botón `Export`: `<button class="xpl-btn-primary">Export</button>`.
  - En el diálogo, ir a la pestaña `Citations` (ej. `<a id="ngb-nav-8" class="nav-link">Citations</a>`).
  - Formato: seleccionar radio “BibTeX” (etiqueta asociada a `for="download-bibtex"`).
  - Contenido: seleccionar radio “Citation and Abstract` (etiqueta asociada a `for="citation-abstract"`).
  - Finalmente, confirmar la descarga (botón de descarga/confirmación en el modal, según la UI actual).

Sugerencia: usar localizadores por texto visible y clases estables, con esperas explícitas hasta que el modal y radios estén clicables.

## Salidas y formatos
- Descargas crudas (.bib) por base: `./resources/downloads/`
  - Se renombran automáticamente como `ScienceDirect_YYYYMMDD_HHMMSS_SSS.bib`, `IEEE_Xplore_YYYYMMDD_HHMMSS_SSS.bib`, etc.

- Agregados por fuente y normalizados: `./resources/data/output/douwnloads/`
  - `sciencedirect_downloads.bib` (concatenación cruda de descargas de SD)
  - `ieee_xplore_downloads.bib` (concatenación cruda de descargas de IEEE)
  - `sciencedirect_normalized.bib` (entradas parseadas y reescritas en BibTeX normalizado)
  - `ieee_xplore_normalized.bib` (entradas parseadas y reescritas en BibTeX normalizado)

- Resultados consolidados/deduplicados: `./resources/data/output/douwnloads/`
  - `resultados_unificados.bib`: salida en BibTeX normalizado de los artículos únicos.
  - `filtered_articles.txt`: cada artículo único se imprime como `@Filtered Article{...}` con los campos pedidos:
    - Ejemplo:
      ```
      @Filtered Article{1129ba99-98fb-4456-b52b-fb25c794cb22,  abstract = {...},  authors = {['Autor 1', 'Autor 2']},  journal = {Revista o booktitle},  keywords = {['kw1', 'kw2']},  title = {Título},  year = {2023} }
      ```
  - `duplicated_articles.txt`: cada duplicado detectado se imprime como `Article{...}` con los campos pedidos:
    - Ejemplo:
      ```
      Article{577c2b10-a4b2-47fc-9772-0fdeb85785c9,  abstract = {...},  authors = {['Autor 1']},  journal = {Revista},  keywords = {['kw']},  title = {Título},  year = {2012} }
      ```

- Formato BibTeX esperado para artículos (ejemplo de entrada normalizada):
  ```
  @article{WU2018127, title = {Single dose testosterone administration modulates emotional reactivity and counterfactual choice in healthy males}, journal = {Psychoneuroendocrinology}, volume = {90}, pages = {127-133}, year = {2018}, issn = {0306-4530}, doi = {https://doi.org/10.1016/j.psyneuen.2018.02.018}, url = {https://www.sciencedirect.com/science/article/pii/S0306453017312532}, author = {Yin Wu y Luke Clark y Samuele Zilioli y Christoph Eisenegger y Claire M. Gillan y Huihua Deng y Hong Li}, keywords = {Testosterone, Reward, Regret, Emotion, Human male, Dual process}, abstract = {...} }
  ```

## Normalización y deduplicación
- Parser: jbibtex transforma cada entrada a la clase de dominio `Article` con campos estandarizados: title, authors, journal/booktitle, year, volume, pages, doi, url, issn/isbn, keywords, abstract.
- Clave de deduplicación:
  - Si hay DOI: `doi:<valor>`.
  - Si no: `title(normalizado, sin puntuación y minúsculas) + year`.
- Fusión de duplicados: prioriza campos no vacíos, une autores/keywords sin repetir y conserva el abstract más largo.

## Logs y diagnóstico
- Archivo: `./logs/application.log`.
- Mensajes relevantes:
  - Inicio/fin por base: `[ScienceDirect] Inicio/Fin`, `[IEEE Xplore] Inicio/Fin`.
  - Descarga/renombrado: `Renombrado nombre_original -> PREFIJO_yyyyMMdd_HHmmss_SSS.bib`.
  - Paginación: `Página x/y` y `No hay más páginas ...`.
  - Advertencias de exportación (típico si no hay sesión):
    - `Fallo exportando en IEEE: No se pudo abrir el diálogo de exportación` → suele indicar sesión no iniciada; realiza el login (SSO/Google) y reintenta.

## Recomendaciones y solución de problemas
- Autenticación:
  - Mantén `automation.headless: false` para poder completar manualmente el 2FA si se requiere.
  - Si usas Google, el flujo intenta hacer clic en `id="btn-google"` cuando está disponible y luego completa email/contraseña si están configurados; ante 2FA, espera tu intervención.
- Cobertura de resultados:
  - Ajusta `automation.max.pages` si necesitas más páginas. Ten en cuenta que las páginas listadas por la UI pueden ser muchas; evita números excesivos si hay limitaciones de sesión/tiempo.
- Estabilidad de selectores:
  - Las UIs de las bases cambian. Si se rompen selectores (no encuentra buscador/exportación), revisa los logs para identificar el paso y actualiza los localizadores.
- Seguridad de credenciales:
  - Evita subir credenciales reales. Usa variables de entorno (`ACADEMIC_EMAIL`, `MAIL_PASSWORD`) y referencia en `application.yml`.

## Componentes principales involucrados
- Controlador: `AutomationController` (`/api/automation/requirement-1`).
- Orquestador: `AutomationOrchestratorService` (dispara scraping en ambas bases y luego unificación).
- Scrapers: `ScienceDirectScraper`, `IEEEXploreScraper` (búsqueda, 100/pg, export, descarga/renombrado).
- Descargas: `DownloadHelper` (espera y renombra el .bib más reciente).
- Parsing/normalización: `BibTeXParserService` (convierte BibTeX → `Article`, exporta BibTeX normalizado y formatos @Filtered/@Duplicated).
- Unificación/deduplicación y salidas: `BibTeXUnificationService` (unifica, divide en únicos y duplicados, y escribe los archivos finales).
