package co.edu.uniquindio.proyectoAlgoritmos.service.scraper;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.helper.DownloadHelper;
import co.edu.uniquindio.proyectoAlgoritmos.util.AutomationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebOfScienceScraper {

    private final SeleniumConfig cfg;
    private final AutomationUtils utils;
    private final DownloadHelper dl;

    public void download(WebDriver d) { download(d, null); }

    public void download(WebDriver d, String query) {
        String searchQuery = (query != null && !query.isBlank()) ? query : cfg.getSearchQuery();
        log.info("[Web of Science] Inicio");
        try {
            // 1) Ir a Smart Search directamente
            final String wosSmart = "https://www.webofscience.com/wos/woscc/smart-search";
            d.get(wosSmart);
            utils.humanDelay();

            // 1.1) Si se redirige a Clarivate Login, completar credenciales
            try {
                String url = d.getCurrentUrl();
                if (url != null && url.contains("access.clarivate.com/login")) {
                    log.info("Detectado login de Clarivate. Intentando autenticación con credenciales configuradas...");
                    performClarivateLogin(d, cfg.getAcademicEmail(), cfg.getPassword());
                    // Esperar que vuelva a Web of Science y reintentar abrir Smart Search
                    for (int i = 0; i < 10; i++) { utils.humanDelay(); try { url = d.getCurrentUrl(); } catch (Exception ignore) {} if (url != null && url.contains("webofscience.com")) break; }
                    d.get(wosSmart);
                    utils.humanDelay();
                }
            } catch (Exception e) {
                log.debug("Chequeo de login Clarivate: {}", e.getMessage());
            }

            // 1.2) Aceptar cookies si aparece (Cookiebot / OneTrust)
            try {
                boolean cookies = utils.clickAny(d, 6,
                        By.id("onetrust-accept-btn-handler"),
                        By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"),
                        By.xpath("//button[contains(.,'Accept all') or contains(.,'Allow all cookies') or contains(.,'Aceptar')]")
                );
                if (cookies) utils.shortDelay();
            } catch (Exception ignore) {}

            // 2) Buscar: usar exactamente el input y botón señalados en Instrucciones.txt
            WebElement search = utils.findAnyVisible(d, 25,
                    By.id("composeQuerySmartSearch"),
                    By.cssSelector("input[name='search-main-box']"),
                    By.cssSelector("input.wos-input")
            );
            if (search == null) throw new RuntimeException("No se encontró el campo de búsqueda composeQuerySmartSearch");
            try { search.clear(); } catch (Exception ignore) {}
            search.sendKeys(searchQuery);
            utils.shortDelay();

            boolean clickedSearch = utils.clickAny(d, 10,
                    By.cssSelector("button.fully-rounded-large-input-submit-button[aria-label='Submit your question']"),
                    By.xpath("//button[@type='submit' and contains(@class,'fully-rounded-large-input-submit-button')]"),
                    By.xpath("//button[@type='submit' and @aria-label='Submit your question']")
            );
            if (!clickedSearch) { search.sendKeys(Keys.ENTER); }
            utils.humanDelay();

            // 3) Abrir Export y escoger BibTeX según Instrucciones.txt
            boolean exportTrigger = utils.clickAny(d, 15,
                    By.id("export-trigger-btn"),
                    By.xpath("//button[contains(@class,'mat-mdc-menu-trigger') and .//span[contains(.,'Export')]]")
            );
            if (!exportTrigger) throw new RuntimeException("No se pudo abrir el menú Export");
            utils.shortDelay();

            boolean chooseBib = utils.clickAny(d, 10,
                    By.id("exportToBibtexButton"),
                    By.xpath("//button[@role='menuitem' and @id='exportToBibtexButton']")
            );
            if (!chooseBib) throw new RuntimeException("No se pudo seleccionar BibTeX en el menú Export");
            utils.humanDelay();

            // 4) En el modal: seleccionar 'Records from' (fromRange) y el contenido 'Author, Title, Source, Abstract'
            ensureFromRangeSelected(d);
            utils.shortDelay();

            // Abrir el dropdown de contenido (muestra inicialmente 'Author, Title, Source')
            boolean openContent = utils.clickAny(d, 10,
                    By.xpath("//button[contains(@class,'dropdown') and @aria-haspopup='listbox' and contains(@aria-label,'Author, Title, Source')]")
            );
            if (!openContent) {
                // Fallback: buscar por texto visible
                openContent = utils.clickAny(d, 8,
                        By.xpath("//button[contains(@class,'dropdown')]//span[contains(@class,'dropdown-text') and contains(.,'Author, Title, Source')]/ancestor::button")
                );
            }
            utils.shortDelay();

            // Elegir 'Author, Title, Source, Abstract'
            boolean pickAbstract = utils.clickAny(d, 10,
                    By.xpath("//div[@id='global-select']//div[@role='menuitem' and (@aria-label='Author, Title, Source, Abstract' or .//span[normalize-space(text())='Author, Title, Source, Abstract'])]")
            );
            if (!pickAbstract) {
                // Fallback: buscar opción por texto plano en cualquier panel abierto
                pickAbstract = utils.clickAny(d, 6,
                        By.xpath("//div[@role='menuitem']//span[normalize-space(text())='Author, Title, Source, Abstract']/parent::div")
                );
            }
            utils.shortDelay();

            // 5) Exportar
            boolean confirm = utils.clickAny(d, 15,
                    By.id("exportButton"),
                    By.xpath("//button[@id='exportButton' or (contains(@class,'mat-mdc-unelevated-button') and .//span[contains(.,'Export')])]"));
            if (!confirm) throw new RuntimeException("No se pudo confirmar la exportación en WOS");
            utils.downloadDelay();

            dl.waitAndRenameLatestBib(cfg.getDownloadDirectory(), "WebOfScience");
        } catch (Exception e) {
            log.warn("Fallo exportando en Web of Science: {}", e.getMessage());
        }
        log.info("[Web of Science] Fin");
    }

    private void performClarivateLogin(WebDriver d, String email, String password) {
        try {
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.info("No hay credenciales configuradas (automation.email/automation.password); se espera login manual si es requerido.");
                return;
            }
            // Campo email
            WebElement emailInput = utils.findAnyVisible(d, 20,
                    By.id("mat-input-0"),
                    By.name("email"),
                    By.cssSelector("input[formcontrolname='email']"),
                    By.cssSelector("input[type='email']")
            );
            if (emailInput != null) { try { emailInput.clear(); } catch (Exception ignore) {} emailInput.sendKeys(email); utils.shortDelay(); }

            // Campo password
            WebElement passInput = utils.findAnyVisible(d, 20,
                    By.id("mat-input-1"),
                    By.name("password"),
                    By.cssSelector("input[formcontrolname='password']"),
                    By.cssSelector("input[type='password']")
            );
            if (passInput != null) { try { passInput.clear(); } catch (Exception ignore) {} passInput.sendKeys(password); utils.shortDelay(); }

            // Botón Sign in
            boolean clicked = utils.clickAny(d, 10,
                    By.id("signIn-btn"),
                    By.name("login-btn"),
                    By.xpath("//button[@type='submit' and (contains(.,'Sign in') or contains(.,'Sign In'))]")
            );
            if (!clicked) log.debug("No se pudo pulsar el botón Sign in (Clarivate)");

            // Esperar redirección a Web of Science
            for (int i = 0; i < 30; i++) {
                utils.humanDelay();
                try {
                    String u = d.getCurrentUrl();
                    if (u != null && (u.contains("webofscience.com") || u.contains("webofknowledge.com"))) break;
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            log.warn("Fallo durante login de Clarivate: {}", e.getMessage());
        }
    }

    // Asegurar que el radiobutton 'Records from:' (fromRange) quede seleccionado
    private void ensureFromRangeSelected(WebDriver d) {
        try {
            // 1) Intentar con el input nativo
            WebElement input = null;
            try { input = d.findElement(By.id("radio3-input")); } catch (Exception ignore) {}
            if (input == null) {
                try { input = d.findElement(By.xpath("//input[@type='radio' and @name='outputMethodType' and @value='fromRange']")); } catch (Exception ignore) {}
            }
            if (input != null) {
                utils.scrollIntoView(d, input);
                if (!input.isSelected()) {
                    try { input.click(); } catch (Exception e) { utils.clickJS(d, input); }
                    utils.shortDelay();
                }
                if (input.isSelected()) { log.debug("'Records from' seleccionado vía input"); return; }
            }

            // 2) Intentar clic en el label asociado
            try {
                WebElement label = d.findElement(By.xpath("//label[@for='radio3-input' and contains(.,'Records from')]"));
                utils.scrollIntoView(d, label);
                try { label.click(); } catch (Exception e) { utils.clickJS(d, label); }
                utils.shortDelay();
                // Validar input de nuevo
                try { if (input == null) input = d.findElement(By.id("radio3-input")); } catch (Exception ignore) {}
                if (input != null && input.isSelected()) { log.debug("'Records from' seleccionado vía label"); return; }
            } catch (Exception ignore) {}

            // 3) Intentar clic en el wrapper del mat-radio-button
            try {
                WebElement wrapper = d.findElement(By.id("radio3"));
                utils.scrollIntoView(d, wrapper);
                try { wrapper.click(); } catch (Exception e) { utils.clickJS(d, wrapper); }
                utils.shortDelay();
                // Validar por estado del input o clase checked
                boolean checked = false;
                try { if (input == null) input = d.findElement(By.id("radio3-input")); } catch (Exception ignore) {}
                if (input != null && input.isSelected()) checked = true;
                try {
                    String cls = wrapper.getAttribute("class");
                    if (cls != null && cls.contains("mat-mdc-radio-checked")) checked = true;
                } catch (Exception ignore) {}
                if (checked) { log.debug("'Records from' seleccionado vía wrapper"); return; }
            } catch (Exception ignore) {}

            // 4) Forzar por JavaScript como último recurso
            try {
                WebElement jsInput = input;
                if (jsInput == null) jsInput = d.findElement(By.xpath("//input[@type='radio' and @name='outputMethodType' and @value='fromRange']"));
                ((JavascriptExecutor) d).executeScript(
                        "arguments[0].checked = true; arguments[0].setAttribute('aria-checked','true'); arguments[0].dispatchEvent(new Event('input',{bubbles:true})); arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                        jsInput);
                utils.shortDelay();
            } catch (Exception ignore) {}
        } catch (Exception e) {
            log.debug("No fue posible asegurar 'Records from': {}", e.getMessage());
        }
    }
}
