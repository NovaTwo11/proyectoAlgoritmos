package co.edu.uniquindio.proyectoAlgoritmos.service.scraper;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.helper.DownloadHelper;
import co.edu.uniquindio.proyectoAlgoritmos.util.AutomationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        log.info("[ACM] Inicio");

        // Autenticación y navegación
        try { d.get("https://dl.acm.org/"); utils.humanDelay(); } catch (Exception ignore) {}

        // Aceptar cookies (Cookiebot)
        try {
            WebElement allowCookies = utils.findAnyVisible(d, 5, By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"));
            if (allowCookies != null) { try { allowCookies.click(); } catch (Exception e) { utils.clickJS(d, allowCookies); } utils.shortDelay(); log.info("[ACM] Cookies aceptadas"); }
        } catch (Exception ignore) {}

        // Buscar con AllField (robusto con reintentos y fallback a URL directa)
        boolean searchOk = false;
        try {
            // Intentar varios localizadores del input en home o en header
            WebElement search = utils.findAnyVisible(d, 20,
                    By.cssSelector("input.auto-complete.quick-search__input[aria-label='Search'][name='AllField']"),
                    By.name("AllField"),
                    By.cssSelector("form.quick-search input[name='AllField']"),
                    By.cssSelector("header input[name='AllField'], header input[aria-label='Search']"),
                    By.cssSelector("input[placeholder*='Search']")
            );
            if (search != null) {
                // Reintentos para evitar StaleElement / overlays
                for (int i = 0; i < 3 && !searchOk; i++) {
                    try {
                        try { search.clear(); } catch (Exception ignore) {}
                        search.sendKeys(searchQuery);
                        search.sendKeys(Keys.ENTER);
                        utils.humanDelay();
                        searchOk = true;
                    } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                        log.debug("[ACM] Reintentando sendKeys por {}", e.getClass().getSimpleName());
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
            }
        } catch (Exception e) {
            log.debug("[ACM] Excepción buscando input: {}", e.getMessage());
        }

        // Fallback: navegar a la URL de resultados directamente si no se logró usar el input
        if (!searchOk) {
            try {
                String encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
                String url = "https://dl.acm.org/action/doSearch?AllField=" + encoded;
                log.info("[ACM] Fallback: navegación directa a {}", url);
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
                WebElement markall = utils.findAnyVisible(d, 8,
                        By.cssSelector("input[type='checkbox'][name='markall'][aria-label='markall']"),
                        By.name("markall")
                );
                if (markall != null && !markall.isSelected()) { try { markall.click(); } catch (Exception e) { utils.clickJS(d, markall); } utils.shortDelay(); }
            } catch (Exception ignore) {}

            // Abrir Export Citations
            boolean openExport = utils.clickAny(d, 12,
                    By.cssSelector("a.btn.light.export-citation[title='Export Citations'][data-target='#exportCitation'][data-toggle='modal']"),
                    By.xpath("//a[@data-toggle='modal' and @data-target='#exportCitation' and contains(.,'Export Citations')]")
            );
            if (!openExport) throw new RuntimeException("No se pudo abrir 'Export Citations'");
            utils.humanDelay();

            // Modal visible
            utils.findAnyVisible(d, 10, By.cssSelector("#exportCitation"), By.id("exportCitation"));

            // Pestaña All Results
            utils.clickAny(d, 10,
                    By.cssSelector("a#allResults[role='tab']"),
                    By.xpath("//a[@id='allResults' and contains(@href,'#allResultstab')]") ,
                    By.xpath("//a[span[normalize-space()='All Results']]")
            );
            utils.shortDelay();

            // Asegurar BibTeX
            try {
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
                }
            } catch (Exception ignore) {}

            // Descargar (Download)
            boolean clickedDownload = utils.clickAny(d, 12,
                    By.cssSelector(".all-results-tab-container a.btn.transparent.downloadBtn[title='Download']"),
                    By.xpath("//div[contains(@class,'all-results-tab-container')]//a[contains(@class,'downloadBtn') and contains(.,'Download')]")
            );
            if (!clickedDownload) throw new RuntimeException("No se pudo iniciar la descarga en ACM");

            // Esperar notificación inicial
            try {
                WebElement notReady = utils.findAnyVisible(d, 5, By.id("exportDownloadNotReady"));
                if (notReady != null) { log.info("[ACM] Preparando archivo... esperando que esté listo"); }
            } catch (Exception ignore) {}

            // Esperar 'Ready' y accionar 'Download now!'
            try {
                long end = System.currentTimeMillis() + 180_000; // 180s
                boolean done = false;
                while (!done && System.currentTimeMillis() < end) {
                    WebElement readyBox = utils.findAnyVisible(d, 5, By.id("exportDownloadReady"));
                    if (readyBox != null) {
                        WebElement downloadNow = null;
                        try {
                            downloadNow = readyBox.findElement(By.xpath(".//a[contains(@class,'btn') and contains(@class,'pull-right') and (contains(translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download now') or contains(.,'Download'))]"));
                        } catch (Exception ignore) {}

                        if (downloadNow != null) {
                            String cls = null, href = null, dataHref = null;
                            try { cls = downloadNow.getAttribute("class"); } catch (Exception ignore2) {}
                            try { href = downloadNow.getAttribute("href"); } catch (Exception ignore2) {}
                            try { dataHref = downloadNow.getAttribute("data-href"); } catch (Exception ignore2) {}

                            boolean classDisabled = (cls != null && cls.toLowerCase().contains("disabled"));
                            log.info("[ACM] Botón 'Download now' detectado. disabled={}", classDisabled);

                            try {
                                utils.scrollIntoView(d, downloadNow);
                                ((JavascriptExecutor) d).executeScript(
                                        "arguments[0].removeAttribute('disabled'); arguments[0].removeAttribute('aria-disabled'); arguments[0].classList.remove('disabled');",
                                        downloadNow
                                );
                                try { downloadNow.click(); } catch (Exception e) { utils.clickJS(d, downloadNow); }
                                utils.humanDelay();
                                log.info("[ACM] 'Download now!' pulsado");

                                boolean started = false;
                                try {
                                    File dir = new File(cfg.getDownloadDirectory());
                                    long t0 = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - t0 < 5000) { // 5s
                                        File[] parts = dir.listFiles((d1, n) -> n.toLowerCase().endsWith(".crdownload") || n.toLowerCase().endsWith(".part") || n.toLowerCase().endsWith(".bib"));
                                        if (parts != null && parts.length > 0) { started = true; break; }
                                        Thread.sleep(300);
                                    }
                                } catch (Exception ignored) {}

                                String finalHref = href != null ? href : dataHref;
                                if (!started && finalHref != null && !finalHref.isBlank()) {
                                    try {
                                        ((JavascriptExecutor) d).executeScript("window.location.href = arguments[0];", finalHref);
                                        utils.humanDelay();
                                        log.info("[ACM] Fallback: navegación directa a {}", finalHref);
                                    } catch (Exception ignore) {}
                                }
                                done = true;
                            } catch (Exception e) {
                                log.warn("[ACM] Error al pulsar 'Download now!': {}", e.getMessage());
                            }
                        }
                    }
                    if (!done) utils.shortDelay();
                }
            } catch (Exception e) {
                log.warn("[ACM] Fallo esperando botón 'Download now!': {}", e.getMessage());
            }

            // Esperar descarga completa de acm.bib y renombrar de forma segura
            try {
                File renamed = dl.waitAndRenameSpecificBib(cfg.getDownloadDirectory(), "acm.bib", "ACM");
                if (renamed == null) {
                    log.warn("[ACM] No se encontró acm.bib para renombrar dentro del tiempo esperado");
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
