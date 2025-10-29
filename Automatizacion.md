# Automatización de descarga de artículos: ScienceDirect, IEEE Xplore y SAGE (guía replicable en Java)

Esta guía describe, de forma agnóstica al lenguaje, cómo automatizar la búsqueda y exportación de citas en formato BibTeX desde ScienceDirect, IEEE Xplore y SAGE, accediendo a través del portal de bibliotecas institucional y autenticación con Google (SSO). Está pensada para replicarse en Java con Selenium WebDriver u otra librería de automatización.

## Objetivo
- Navegar al portal institucional de bases de datos.
- Acceder a ScienceDirect, IEEE Xplore y SAGE a través de la pasarela de la universidad.
- Iniciar sesión con Google (correo institucional y contraseña).
- Ejecutar una búsqueda (ej. "computational thinking").
- Configurar 100 resultados por página cuando sea posible.
- Seleccionar todos los resultados visibles, exportar citas en BibTeX y descargar.
- Paginación y repetición del proceso hasta completar las páginas deseadas.

## Requisitos previos
- Navegador Chromium compatible (Google Chrome o Microsoft Edge) actualizado.
- WebDriver compatible con el navegador elegido (ChromeDriver o EdgeDriver). En Java puedes usar Selenium WebDriver y, opcionalmente, WebDriverManager para gestionar drivers.
- JDK 11+ (recomendado 17+) si lo replicarás en Java; gestor de dependencias (Maven/Gradle) para añadir Selenium.
- Credenciales válidas de acceso institucional vía Google (SSO) al portal de la biblioteca.
- Variables/almacenamiento seguro de secretos para el correo y la contraseña. Recomendado: variables de entorno como ACADEMIC_EMAIL y MAIL_PASSWORD.
- Carpeta de descargas configurada (idealmente dedicada) y permisos para descargar múltiples archivos sin prompts.

Notas prácticas:
- Algunos portales muestran banners de cookies o modales que deben cerrarse antes de interactuar con la página.
- Si la plataforma detecta automatización, considera usar un perfil de navegador real (user data dir), pausas aleatorias, desplazamientos de página y esperas explícitas.

## Flujo común (aplicable a las tres plataformas)
1. Abrir el portal institucional de bases de datos: `https://library.uniquindio.edu.co/databases`.
2. Localizar la tarjeta/enlace de la base de datos objetivo y navegar al destino (pasando por la pasarela institucional que concede acceso).
3. Iniciar sesión con Google:
    - Hacer clic en el botón de inicio de sesión con Google (si se ofrece).
    - Introducir correo institucional (ej. `usuario@...edu.co`).
    - Introducir contraseña (tomada de un almacén seguro/variable de entorno).
    - Completar los pasos del flujo SSO.
4. Ejecutar la búsqueda:
    - Escribir el término (ej. "computational thinking") en el campo de búsqueda principal.
    - Enviar la búsqueda.
5. Ajustar “resultados por página” a 100, si el sitio lo permite.
6. En cada página de resultados:
    - Seleccionar todos los elementos visibles (checkbox o control equivalente).
    - Abrir el menú/botón de exportación de citas.
    - Elegir formato BibTeX.
    - Confirmar/descargar.
    - Cerrar cualquier modal emergente tras la descarga.
    - Navegar a la siguiente página y repetir.

## Detalle por plataforma

### ScienceDirect (Elsevier)
- Acceso: desde el portal institucional, entrar a la tarjeta/enlace de ScienceDirect.
- Búsqueda: localizar el campo principal de búsqueda (habitualmente en la cabecera) e introducir el término; enviar la búsqueda.
- Resultados por página: buscar el control de “Results per page” y seleccionar 100.
- Selección masiva: utilizar el control de “Seleccionar todo” en la cabecera de resultados.
- Exportación:
    - Abrir la acción de “Export/Export all citations”.
    - Elegir la opción BibTeX en el diálogo de exportación.
    - Confirmar para iniciar la descarga del archivo `.bib`.
- Paginación: usar el control “Siguiente” o equivalente; repetir selección y exportación por cada página.

Consejos:
- Emplea esperas explícitas para elementos dinámicos (resultados, diálogos de exportación, paginación).
- Tras exportar, algunos sitios dejan la selección activa; desmarca si es necesario antes de cambiar de página.

### IEEE Xplore
- Acceso: entrar desde el portal institucional a la tarjeta/enlace de IEEE Xplore.
- Búsqueda: en el campo de búsqueda de la parte superior, introducir el término y ejecutar.
- Cookies: cerrar el banner de consentimiento si aparece antes de continuar.
- Resultados por página: abrir el selector de cantidad y cambiar a 100 por página.
- Selección masiva: activar “Seleccionar todo” en la página de resultados.
- Exportación:
    - Abrir el menú de exportación de resultados.
    - Ir a la sección/pestaña de “Citations”.
    - Elegir el formato BibTeX.
    - Descargar el archivo `.bib` y cerrar cualquier modal que quede abierto.
- Paginación: avanzar a la siguiente página con el control “Next” y repetir.

Consejos:
- Algunos diálogos de exportación contienen varias pestañas (RIS, BibTeX, etc.); verifica que BibTeX está marcado antes de descargar.
- Cierra los pop-ups/modales de forma fiable antes de seguir paginando.

### SAGE Journals
- Acceso: entrar desde el portal institucional a la tarjeta/enlace de SAGE.
- Búsqueda: localizar el campo de búsqueda principal, introducir el término y ejecutar.
- Cookies: aceptar el banner de cookies si bloquea la interacción.
- Selección masiva: usar el checkbox “Seleccionar todo” de la barra de acciones.
- Exportación:
    - Abrir la opción de “Export citation”.
    - En el selector de formato, elegir “BibTeX”.
    - Confirmar/descargar y cerrar el modal de exportación.
- Paginación: desplazarse hasta el pie de página y utilizar el control de “Siguiente”; repetir selección y exportación por cada página.

Consejos:
- En algunos listados, el botón “Siguiente” sólo aparece tras hacer scroll hasta el final de la página.
- Inserta pequeñas pausas entre acciones para evitar bloqueos por uso intensivo.

## Descargas y archivos de salida
- El navegador descargará uno o varios `.bib` por página según la plataforma y la configuración.
- Configura el directorio de descargas y la política de descarga automática (sin prompts) para evitar bloqueos.
- En este repositorio, los archivos `.bib` se consolidan/filtran en `output_files/`, y hay ejemplos de entradas provenientes de ScienceDirect en `researchFiles/` (puedes replicar el patrón en Java).

## Buenas prácticas y cumplimiento
- Revisa y respeta los Términos de Uso de cada plataforma y del proveedor institucional.
- Limita la tasa de solicitudes; agrega esperas y evita patrones de clics excesivamente robóticos.
- No compartas credenciales. Usa un gestor de secretos o variables de entorno.
- Si el acceso requiere presencia humana (CAPTCHA, 2FA), considera integrar pasos manuales supervisados.

## Sugerencias para implementación en Java
- Usa Selenium WebDriver (con Maven/Gradle) y el driver del navegador correspondiente.
- Estructura el flujo en funciones/métodos que representen: apertura de portal, login SSO, búsqueda, ajuste de resultados por página, selección, exportación, paginación.
- Implementa esperas explícitas para elementos clave (buscador, selector de resultados por página, botón de exportación, diálogo de formato, botón siguiente).
- Maneja pop-ups de cookies y modales de exportación de manera robusta (detectar, cerrar, reintentar si es necesario).
- Considera usar un perfil de navegador persistente para mantener sesiones y reducir fricción en el SSO.

## Solución de problemas
- El botón de exportación no aparece: verifica que hay resultados y que se han seleccionado; espera a que la página termine de renderizar.
- Descarga bloqueada por el navegador: habilita descargas múltiples automáticas y define la carpeta destino.
- Bucle de login: borra cookies/sesión o utiliza un perfil de navegador; verifica que el correo institucional tiene acceso a la base de datos.
- Detección de automatización: simula interacciones humanas (scroll, pausas), usa perfiles reales y evita acciones demasiado rápidas.

---

Con esta guía podrás replicar el proceso en Java sin depender de detalles específicos del código original, manteniendo el mismo flujo de trabajo para las tres plataformas.

## Anexo: selectores CSS/XPath útiles por plataforma

Estos selectores son orientativos; valida en tu sesión ya que algunos IDs/clases pueden variar con el tiempo.

### Portal institucional y SSO
- Portal de bases de datos: `https://library.uniquindio.edu.co/databases`
- Botón “Continuar con Google” (en pasarela): `#btn-google`

### ScienceDirect
- Tarjeta/enlace en el portal: `#facingenierasciencedirectdescubridor a`
- Campo de búsqueda: `#qs`
- Selector “resultados por página”: `.ResultsPerPage li:nth-child(3) a` (suele ser 100)
- Contenedor de cabecera para “seleccionar todo”: `.result-header-controls-container` (clic en el `span` clickable dentro)
- Botón exportar: `.export-all-link-button`
- Diálogo de exportación: `.ExportCitationOptions .preview-body`
- Botón BibTeX en el diálogo: tercer `button` dentro de `.preview-body` o usar texto “BibTeX” si está disponible
- Paginación siguiente: `.next-link a`

XPath alternativos resilientes:
- Campo de búsqueda: `//input[@id='qs']`
- Botón BibTeX: `//div[contains(@class,'ExportCitationOptions')]//button[contains(.,'BibTeX')]`

### IEEE Xplore
- Tarjeta/enlace en el portal: `#facingenieraieeeinstituteofelectricalandelectronicsengineersdescubridor a`
- Campo de búsqueda: `.Typeahead-input`
- Aceptar cookies: `.osano-cm-save`
- Resultados por página (abrir menú): `#dropdownPerPageLabel`
- Opción “100 por página”: `.dropdown-menu button` (elige la que diga “100”)
- Seleccionar todo: `.results-actions-selectall-checkbox`
- Abrir exportación: `.export-filter button`
- Navegación a pestaña “Citations”: `.nav-tabs li:nth-child(2) a`
- Contenedor del formulario de exportación: `.export-form`
- Opción BibTeX: busca un `label`/`input` con texto “BibTeX” dentro de la sección de citas
- Descargar: `.stats-SearchResults_Citation_Download`

XPath alternativos resilientes:
- Pestaña Citations: `//ul[contains(@class,'nav-tabs')]/li[a[contains(.,'Citations')]]/a`
- BibTeX radio: `//form[contains(@class,'export-form')]//label[contains(.,'BibTeX')]/input`

### SAGE Journals
- Tarjeta/enlace en el portal: `#facingenierasagerevistasconsorciocolombiadescubridor a`
- Aceptar cookies: `#onetrust-accept-btn-handler`
- Campo de búsqueda (ID puede variar): usa un XPath más flexible como `//input[starts-with(@id,'AllField') or @name='AllField']`
- Seleccionar todo: `#action-bar-select-all`
- Exportar citas: `.export-citation`
- Selector de formato: `select[name='citation-format']` (elige “BibTeX” por texto visible)
- Confirmar exportación: `.form-buttons a`
- Cerrar modal: `.modal__header button`
- Ir a la siguiente página: `.hvr-forward`

Sugerencias de robustez:
- Prefiere XPath por texto visible (contains(.,'BibTeX')) y selectores por rol/aria cuando existan.
- Implementa esperas explícitas (elementToBeClickable, visibilityOfElementLocated) en cada interacción clave.

## Configuración de descargas automáticas y perfiles en Java

Ejemplo con Selenium (Chrome) para definir carpeta de descargas, evitar prompts y usar un perfil persistente:

```java
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DriverFactory {
  public static WebDriver createChrome() {
    String downloadDir = Paths.get("C:", "data", "downloads").toString();

    Map<String, Object> prefs = new HashMap<>();
    prefs.put("download.default_directory", downloadDir);
    prefs.put("download.prompt_for_download", false);
    prefs.put("download.directory_upgrade", true);
    prefs.put("safebrowsing.enabled", true);
    // Permitir múltiples descargas automáticas
    prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
    // Abrir PDFs fuera del visor integrado para forzar descarga
    prefs.put("plugins.always_open_pdf_externally", true);

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--start-maximized");
    options.addArguments("--lang=es-ES");
    options.setExperimentalOption("prefs", prefs);

    // Usar un perfil persistente (opcional, útil para SSO)
    // Cambia la ruta a una carpeta válida en tu sistema
    options.addArguments("--user-data-dir=" + Paths.get(System.getProperty("user.home"),
        "AppData", "Local", "Google", "Chrome", "User Data", "auto-scrape-profile").toString());

    return new ChromeDriver(options);
  }
}
```

Notas:
- En Windows, asegúrate de que la ruta de `download.default_directory` y `--user-data-dir` exista y sea accesible.
- Para Microsoft Edge, usa `EdgeOptions` con la misma clave de preferencias `prefs`.
- Si usas WebDriverManager, inicialízalo antes de crear el driver. Ej.: `WebDriverManager.chromedriver().setup();`
- Para evitar bloqueos por visor PDF integrado, mantén `plugins.always_open_pdf_externally=true`.
