# Requerimiento 1 — Extracción, normalización y unificación de artículos (ScienceDirect e IEEE Xplore)

Este documento describe qué se busca con el Requerimiento 1 y cómo se implementa en este proyecto: automatizar búsquedas en ScienceDirect e IEEE Xplore, descargar las citas en formato BibTeX con abstract, unificar y normalizar los registros, y detectar/eliminar duplicados. También detalla rutas de salida, parámetros y solución de problemas.

## Objetivo
- Automatizar el navegador para cada base de datos (ScienceDirect, IEEE Xplore).
- Autenticarse (proxy institucional/SSO) para habilitar exportación de citas.
- Ejecutar la búsqueda por una cadena dada, configurar 100 resultados/página, seleccionar todos y exportar en BibTeX incluyendo el abstract.
- Guardar cada .bib descargado con nombre único en resources/downloads.
- Parsear todos los .bib descargados, normalizar campos (título, autores, año, abstract, DOI, URL, journal/booktitle, etc.), detectar duplicados por DOI o título normalizado + año, asignar UUID internos y exportar:
  - Unificados en BibTeX con el formato solicitado.
  - Filtrados y duplicados con el formato solicitado (@Filtered Article, Article{...}).
  - Archivos agregados por fuente (crudo y normalizado) en una carpeta de salida establecida.

## Flujo implementado

### ScienceDirect
- Navegación directa al dominio institucional: https://www-sciencedirect-com.crai.referencistas.com/
- Apertura de la tarjeta de inicio de sesión desde el header (id="gh-myaccount-btn").
- Se completa la contraseña en el campo id="bdd-password". El email suele estar pre-llenado (id="bdd-disabledEmail"); si existiera un campo editable (id="bdd-email"), se rellena con automation.email.
- Búsqueda: se localiza el input (id="qs" o equivalentes) y se envía la query.
- Resultados por página: se intenta forzar 100 usando menú/select.
- Selección: se intenta “Select all” con varios selectores robustos.
- Exportación: se abre el diálogo y se intenta elegir BibTeX; se espera la descarga y se renombra el archivo con prefijo “ScienceDirect”.
- Paginación: se avanza hasta el límite configurado (automation.max.pages).

### IEEE Xplore
- Home: https://ieeexplore.ieee.org/Xplore/home.jsp y aceptación de cookies si aplica.
- Sign In personal si las credenciales están configuradas; si no, continúa sin login (es posible que ciertas exportaciones requieran sesión).
- Búsqueda: escribe la query en el input Typeahead y pulsa el botón lupa (o Enter como fallback).
- Items Per Page → 100.
- Seleccionar todos: se emplea un método idempotente que sólo marca el checkbox si está desmarcado para evitar el “toggle” rápido (“Select All on Page”).
- Export → pestaña “Citations” → formato “BibTeX” → “Citation and Abstract” → descargar; el archivo se renombra con prefijo “IEEE_Xplore”.
- Paginación hasta automation.max.pages.

## Normalización, unificación y deduplicación
- Parser: lee todos los .bib de resources/downloads.
- Modelo Article incluye UUID interno, tipo de entrada (article, incollection, etc.), título, autores, journal o booktitle, año, volumen, páginas, DOI, URL, ISSN/ISBN, keywords y abstract.
- Dedupe: clave por DOI (si existe) o por título normalizado + año.
- Merge: conserva la información más completa (por ejemplo, abstract más largo, unión de autores/keywords sin duplicados).

## Salidas generadas
Se escriben en la carpeta:
- resources/data/output/douwnloads

Archivos:
- resultados_unificados.bib → todos los artículos consolidados en formato BibTeX con campos: title, journal/booktitle, volume, pages, year, issn/isbn, doi, url, author (unido con “and”), keywords (unido por comas), abstract.
- resultados_duplicados.txt → líneas tipo Article{UUID, ...} para duplicados detectados.
- filtered_articles.txt → cada línea como @Filtered Article{UUID, ...} (registros unificados/filtrados).
- duplicated_articles.txt → duplicados en formato Article{...} (idéntico al solicitado).
- sciencedirect_downloads.bib, ieee_xplore_downloads.bib → agregados “crudos” por fuente.
- sciencedirect_normalized.bib, ieee_xplore_normalized.bib → mismos artículos por fuente pero normalizados al formato BibTeX de salida.

Nota: cada descarga individual .bib se guarda en resources/downloads con un timestamp y un prefijo de fuente.

## Parámetros y configuración
- application.yml
  - automation.email / automation.password → credenciales (recomendado usar variables de entorno, ver abajo).
  - automation.search.query → query por defecto si no se envía por API.
  - automation.download.directory → carpeta de descargas, por defecto ./resources/downloads.
  - automation.portal.url → URL del portal institucional (fallback).
  - automation.max.pages → número máximo de páginas a procesar (por defecto 3). Si necesitas “todas”, usa un valor alto (p. ej. 9999). También se puede evolucionar para interpretar -1 como “todas las disponibles”.
  - automation.headless → si se desea ejecutar sin UI.

Para evitar exponer secretos, puedes referenciar variables de entorno:

```yaml
automation:
  email: "${ACADEMIC_EMAIL:}"
  password: "${MAIL_PASSWORD:}"
```

En Windows (cmd.exe) antes de arrancar la app:

```bat
set ACADEMIC_EMAIL=tu_correo@dominio.edu
set MAIL_PASSWORD=tu_password_secreta
.\n\mvnw.cmd spring-boot:run
```

## API para ejecutar el requerimiento
- Endpoint: POST /api/automation/requirement-1
  - Parámetros: query (opcional). Si no se envía, usa automation.search.query.
  - Comportamiento: ejecuta el flujo de ScienceDirect e IEEE Xplore, descarga, unifica y deja resultados en resources/data/output/douwnloads.

Ejemplo (PowerShell/cmd invocando con curl):

```bat
curl -X POST "http://localhost:8080/api/automation/requirement-1?query=generative+artificial+intelligence"
```

## Solución de problemas
- “No se pudo abrir el diálogo de exportación” (IEEE): suele requerir sesión o que el checkbox “Select All on Page” esté realmente marcado. El scraper intenta abrir el modal, cambiar a la pestaña “Citations”, seleccionar BibTeX y “Citation and Abstract”. Si la UI cambia, pueden requerirse ajustes de selectores.
- “Select All on Page” se marca y desmarca: el scraper ahora usa una rutina idempotente que valida el estado real del input y, si es necesario, fuerza el checked vía JavaScript, evitando el “toggle”.
- ScienceDirect login: se intenta primero el dominio institucional y el botón de “Mi cuenta” (gh-myaccount-btn). Se rellena bdd-password; si el email editable está disponible (bdd-email), se usa automation.email. Si falla, se hace fallback por proxy y/o portal.
- 2FA/Google: la autenticación con Google y 2FA no se automatiza de forma fiable; si el flujo exige 2FA, suele requerir intervención manual.
- 100 resultados por página: en ambos sitios se intenta forzar 100/pg; si la UI cambia, hay selectores alternativos. 
- CDP warnings de Selenium: son avisos de compatibilidad; WebDriverManager resuelve el chromedriver adecuado. Si fallara, actualiza dependencias de selenium-devtools.

## Criterios de éxito
- Descarga correcta de BibTeX con abstract de ambas bases.
- Archivos de salida presentes en resources/data/output/douwnloads.
- Unificación sin errores y detección de duplicados coherente (por DOI o título normalizado + año).
- Permite limitar por páginas o ampliar para recorrer “todos” los resultados.

## Próximos pasos sugeridos
- Añadir opción “maxPages=-1” para procesar todas las páginas automáticamente.
- Soportar login Google (click en id="btn-google") con pausa manual para 2FA.
- Pruebas automatizadas unitarias del parser y detección de duplicados.
- Mejorar robustez de selectores ante cambios de frontend en las bases de datos.

