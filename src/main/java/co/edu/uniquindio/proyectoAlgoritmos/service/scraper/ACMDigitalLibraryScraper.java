package co.edu.uniquindio.proyectoAlgoritmos.service.scraper;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.helper.DownloadHelper;
import co.edu.uniquindio.proyectoAlgoritmos.util.AutomationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ACMDigitalLibraryScraper {

    private final SeleniumConfig cfg;
    private final AutomationUtils utils;
    private final DownloadHelper dl;


    public void download(WebDriver d) { download(d, null); }

    public void download(WebDriver d, String query) {
        String searchQuery = (query != null && !query.isBlank()) ? query : cfg.getSearchQuery();
        log.info("[ACM] Inicio | query='{}'", searchQuery);

        // Navegar a home
        try {
            log.info("[ACM] Navegando a home https://dl.acm.org/");
            d.get("https://dl.acm.org/");
        } catch (Exception ignore) {
            log.warn("[ACM] Error al navegar a home: {}", ignore.getMessage());
        }

        // NUEVO: aceptar cookies inmediatamente al cargar la página antes de cualquier interacción
        try {
            log.info("[ACM] Intentando aceptar cookies (Cookiebot)");
            WebDriverWait wait = new WebDriverWait(d, Duration.ofSeconds(8));
            WebElement cookieBtn = null;
            // Intentos secuenciales de distintos selectores
            try { cookieBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"))); log.debug("[ACM] Cookie button localizado por id"); } catch (Exception ignore) {}
            if (cookieBtn == null) {
                try { cookieBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@id='CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll' or normalize-space(text())='Allow all cookies']"))); log.debug("[ACM] Cookie button localizado por xpath alterno"); } catch (Exception ignore) {}
            }
            if (cookieBtn == null) {
                try { cookieBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.CybotCookiebotDialogBodyButton#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"))); log.debug("[ACM] Cookie button localizado por css alterno"); } catch (Exception ignore) {}
            }
            if (cookieBtn != null) {
                try { cookieBtn.click(); log.info("[ACM] Cookies: clic normal sobre 'Allow all cookies'"); } catch (Exception e) { utils.clickJS(d, cookieBtn); log.info("[ACM] Cookies: clic via JS sobre 'Allow all cookies'"); }
                utils.shortDelay();
            } else {
                log.info("[ACM] Botón de cookies no encontrado en el tiempo límite (posible no requerido)");
            }
        } catch (Exception e) {
            log.debug("[ACM] Error al aceptar cookies: {}", e.getMessage());
        }

        // Fallback cookies
        try {
            WebElement allowCookies = utils.findAnyVisible(d, 5, By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"));
            if (allowCookies != null) { try { allowCookies.click(); log.info("[ACM] Cookies fallback: clic normal"); } catch (Exception e) { utils.clickJS(d, allowCookies); log.info("[ACM] Cookies fallback: clic via JS"); } utils.shortDelay(); }
        } catch (Exception ignore) {}

        // Buscar con AllField (robusto con reintentos y fallback a URL directa)
        boolean searchOk = false;
        try {
            log.info("[ACM] Buscando input AllField para enviar query");
            WebElement search = utils.findAnyVisible(d, 20,
                    By.cssSelector("input.auto-complete.quick-search__input[aria-label='Search'][name='AllField']"),
                    By.name("AllField"),
                    By.cssSelector("form.quick-search input[name='AllField']"),
                    By.cssSelector("header input[name='AllField'], header input[aria-label='Search']"),
                    By.cssSelector("input[placeholder*='Search']")
            );
            if (search != null) {
                log.info("[ACM] Input de búsqueda localizado. Enviando query...");
                for (int i = 0; i < 3 && !searchOk; i++) {
                    try {
                        log.debug("[ACM] Intento {} de envío de búsqueda", i + 1);
                        try { search.clear(); } catch (Exception ignore) {}
                        search.sendKeys(searchQuery);
                        search.sendKeys(Keys.ENTER);
                        utils.humanDelay();
                        searchOk = true;
                        log.info("[ACM] Búsqueda enviada con éxito por input");
                    } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                        log.debug("[ACM] Reintentando por {}", e.getClass().getSimpleName());
                        utils.shortDelay();
                        try {
                            search = utils.findAnyVisible(d, 8,
                                    By.name("AllField"),
                                    By.cssSelector("input.auto-complete.quick-search__input[aria-label='Search'][name='AllField']"));
                        } catch (Exception ignore) {}
                    } catch (TimeoutException te) {
                        log.debug("[ACM] Timeout enviando búsqueda, se intentará fallback");
                        break;
                    } catch (Exception e) {
                        log.debug("[ACM] Falla al iniciar búsqueda: {}", e.getMessage());
                        break;
                    }
                }
            } else {
                log.info("[ACM] No se localizó input de búsqueda visible (se usará fallback)");
            }
        } catch (Exception e) {
            log.debug("[ACM] Excepción buscando input: {}", e.getMessage());
        }

        // Fallback: navegar a la URL de resultados directamente si no se logró usar el input
        if (!searchOk) {
            try {
                String encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
                String url = "https://dl.acm.org/action/doSearch?AllField=" + encoded;
                log.info("[ACM] Fallback de búsqueda: navegando a {}", url);
                d.get(url);
                utils.humanDelay();
            } catch (Exception e) {
                log.warn("[ACM] Fallback de búsqueda falló: {}", e.getMessage());
            }
        }

        // Exportación global (una sola vez)
        try {
            // Seleccionar todos en página (por robustez)
            try {
                log.info("[ACM] Intentando marcar 'markall' en resultados");
                WebElement markall = utils.findAnyVisible(d, 8,
                        By.cssSelector("input[type='checkbox'][name='markall'][aria-label='markall']"),
                        By.name("markall")
                );
                if (markall != null && !markall.isSelected()) { try { markall.click(); log.info("[ACM] 'markall' marcado"); } catch (Exception e) { utils.clickJS(d, markall); log.info("[ACM] 'markall' marcado via JS"); } utils.shortDelay(); } else { log.info("[ACM] 'markall' no disponible o ya estaba marcado"); }
            } catch (Exception e) { log.debug("[ACM] No fue posible marcar 'markall': {}", e.getMessage()); }

            // Abrir Export Citations
            log.info("[ACM] Abriendo modal 'Export Citations'");
            boolean openExport = utils.clickAny(d, 12,
                    By.cssSelector("a.btn.light.export-citation[title='Export Citations'][data-target='#exportCitation'][data-toggle='modal']"),
                    By.xpath("//a[@data-toggle='modal' and @data-target='#exportCitation' and contains(.,'Export Citations')]")
            );
            if (!openExport) throw new RuntimeException("No se pudo abrir 'Export Citations'");
            utils.humanDelay();

            // Modal visible
            WebElement modal = utils.findAnyVisible(d, 10, By.cssSelector("#exportCitation"), By.id("exportCitation"));
            if (modal != null) { utils.scrollIntoView(d, modal); log.info("[ACM] Modal 'Export Citation' visible"); } else { log.warn("[ACM] Modal 'Export Citation' no visible"); }

            // Pestaña All Results
            log.info("[ACM] Seleccionando pestaña 'All Results'");
            boolean tabOk = utils.clickAny(d, 10,
                    By.cssSelector("a#allResults[role='tab']"),
                    By.xpath("//a[@id='allResults' and contains(@href,'#allResultstab')]") ,
                    By.xpath("//a[span[normalize-space()='All Results']]")
            );
            if (!tabOk) log.warn("[ACM] No se pudo seleccionar pestaña 'All Results'");
            utils.shortDelay();

            // Asegurar BibTeX
            try {
                log.info("[ACM] Intentando forzar formato 'BibTeX'");
                WebElement select = utils.findAnyVisible(d, 8,
                        By.cssSelector("select#citation-format[aria-label='Select Citation format']"),
                        By.id("citation-format")
                );
                if (select != null) {
                    ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                            "arguments[0].value='bibtex'; arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                            select
                    );
                    utils.shortDelay();
                    log.info("[ACM] Formato 'BibTeX' establecido");
                } else {
                    log.warn("[ACM] Selector de formato de cita no encontrado");
                }
            } catch (Exception e) { log.warn("[ACM] No se pudo establecer formato 'BibTeX': {}", e.getMessage()); }

            // Descargar (Download) – ampliar selectores dentro del modal All Results
            log.info("[ACM] Iniciando descarga (clic 'Download')");
            boolean clickedDownload = utils.clickAny(d, 15,
                    By.cssSelector(".all-results-tab-container a.btn.transparent.downloadBtn[title='Download']"),
                    By.xpath("//div[contains(@class,'all-results-tab-container')]//a[contains(@class,'downloadBtn') and contains(.,'Download')]") ,
                    By.xpath("//div[@id='exportCitation']//a[contains(@class,'downloadBtn') and contains(.,'Download')]")
            );
            if (!clickedDownload) throw new RuntimeException("No se pudo iniciar la descarga en ACM");

            // Esperar notificación inicial
            try {
                WebElement notReady = utils.findAnyVisible(d, 5, By.id("exportDownloadNotReady"));
                if (notReady != null) { log.info("[ACM] 'Preparing file...' detectado"); }
            } catch (Exception ignore) {}

            // Esperar 'Ready' y accionar 'Download now!'
            try {
                long end = System.currentTimeMillis() + 180_000; // 180s
                boolean done = false;
                while (!done && System.currentTimeMillis() < end) {
                    WebElement readyBox = utils.findAnyVisible(d, 5, By.id("exportDownloadReady"));
                    if (readyBox != null) {
                        log.info("[ACM] 'Your file is ready' detectado");
                        WebElement downloadNow = null;
                        try {
                            downloadNow = readyBox.findElement(By.xpath(".//a[contains(@class,'btn') and contains(@class,'pull-right') and (contains(translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download now') or contains(.,'Download'))]"));
                        } catch (Exception ignore) {}

                        if (downloadNow != null) {
                            try {
                                utils.scrollIntoView(d, downloadNow);
                                ((JavascriptExecutor) d).executeScript(
                                        "arguments[0].removeAttribute('disabled'); arguments[0].removeAttribute('aria-disabled'); arguments[0].classList.remove('disabled');",
                                        downloadNow
                                );
                                try { downloadNow.click(); log.info("[ACM] Clic en 'Download now!' (normal)"); } catch (Exception e) { utils.clickJS(d, downloadNow); log.info("[ACM] Clic en 'Download now!' (JS)"); }
                                utils.humanDelay();
                                done = true;
                            } catch (Exception e) {
                                log.warn("[ACM] Error al pulsar 'Download now!': {}", e.getMessage());
                            }
                        } else {
                            log.debug("[ACM] Botón 'Download now' no localizado aún dentro del popup");
                        }
                    }
                    if (!done) utils.shortDelay();
                }
                if (!done) log.warn("[ACM] Timeout esperando 'Download now!'");
            } catch (Exception e) {
                log.warn("[ACM] Fallo esperando botón 'Download now!': {}", e.getMessage());
            }

            // Esperar descarga completa SOLO de acm.bib y renombrar con prefijo "ACM"
            try {
                log.info("[ACM] Esperando archivo 'acm.bib' en {} para renombrar", cfg.getDownloadDirectory());
                File renamed = dl.waitAndRenameSpecificBib(cfg.getDownloadDirectory(), "acm.bib", "ACM");
                if (renamed == null) {
                    log.warn("[ACM] No se detectó acm.bib para renombrar dentro del tiempo esperado");
                } else {
                    log.info("[ACM] Archivo renombrado correctamente: {}", renamed.getAbsolutePath());
                }
            } catch (Exception ex) {
                log.warn("[ACM] Error esperando/renombrando acm.bib: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.warn("[ACM] Fallo exportando: {}", e.getMessage());
        }

        log.info("[ACM] Fin");
    }
}
