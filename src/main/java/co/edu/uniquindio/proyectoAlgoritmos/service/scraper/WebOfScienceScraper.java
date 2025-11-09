package co.edu.uniquindio.proyectoAlgoritmos.service.scraper;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.helper.DownloadHelper;
import co.edu.uniquindio.proyectoAlgoritmos.util.AutomationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        log.info("[Web of Science] Inicio | query='{}'", searchQuery);
        try {
            // 1) Ir a Smart Search directamente
            final String wosSmart = "https://www.webofscience.com/wos/woscc/smart-search";
            log.info("[Web of Science] Navegando a Smart Search: {}", wosSmart);
            d.get(wosSmart);
            utils.humanDelay();

            // 1.1) Si se redirige a Clarivate Login, completar credenciales
            try {
                String url = d.getCurrentUrl();
                log.debug("[Web of Science] URL actual tras navegación inicial: {}", url);
                if (url != null && url.contains("access.clarivate.com/login")) {
                    log.info("[Web of Science] Detectado login de Clarivate. Autenticando con credenciales configuradas...");
                    performClarivateLogin(d, cfg.getAcademicEmail(), cfg.getPassword());
                    // Esperar que vuelva a Web of Science y reintentar abrir Smart Search
                    for (int i = 0; i < 10; i++) {
                        utils.humanDelay();
                        try { url = d.getCurrentUrl(); } catch (Exception ignored) {}
                        log.debug("[Web of Science] URL tras intento de login {}: {}", i + 1, url);
                        if (url != null && url.contains("webofscience.com")) break;
                    }
                    log.info("[Web of Science] Volviendo a Smart Search tras login");
                    d.get(wosSmart);
                    utils.humanDelay();
                }
            } catch (Exception e) {
                log.debug("[Web of Science] Chequeo de login Clarivate falló: {}", e.getMessage());
            }

            // 1.2) Aceptar cookies si aparece (Cookiebot / OneTrust)
            try {
                log.info("[Web of Science] Intentando aceptar cookies (OneTrust/Cookiebot)");
                boolean cookies = utils.clickAny(d, 6,
                        By.id("onetrust-accept-btn-handler"),
                        By.id("CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll"),
                        By.xpath("//button[contains(.,'Accept all') or contains(.,'Allow all cookies') or contains(.,'Aceptar')]")
                );
                log.info("[Web of Science] Resultado aceptar cookies: {}", cookies);
                if (cookies) utils.shortDelay();
            } catch (Exception ignored) {}

            // 2) Buscar: usar exactamente el input y botón señalados en Instrucciones.txt
            log.info("[Web of Science] Buscando input de Smart Search");
            WebElement search = utils.findAnyVisible(d, 25,
                    By.id("composeQuerySmartSearch"),
                    By.cssSelector("input[name='search-main-box']"),
                    By.cssSelector("input.wos-input")
            );
            if (search == null) throw new RuntimeException("No se encontró el campo de búsqueda composeQuerySmartSearch");
            try { search.clear(); } catch (Exception ignored) {}
            log.info("[Web of Science] Enviando query de búsqueda");
            search.sendKeys(searchQuery);
            utils.shortDelay();

            log.info("[Web of Science] Intentando enviar búsqueda (botón submit)");
            boolean clickedSearch = utils.clickAny(d, 10,
                    By.cssSelector("button.fully-rounded-large-input-submit-button[aria-label='Submit your question']"),
                    By.xpath("//button[@type='submit' and contains(@class,'fully-rounded-large-input-submit-button')]"),
                    By.xpath("//button[@type='submit' and @aria-label='Submit your question']")
            );
            if (!clickedSearch) { log.debug("[Web of Science] Botón submit no clickeable, probando ENTER"); search.sendKeys(Keys.ENTER); }
            utils.humanDelay();

            // 3) Abrir Export y escoger BibTeX
            log.info("[Web of Science] Abriendo menú Export");
            boolean exportTrigger = utils.clickAny(d, 15,
                    By.id("export-trigger-btn"),
                    By.xpath("//button[contains(@class,'mat-mdc-menu-trigger') and .//span[contains(.,'Export')]]")
            );
            if (!exportTrigger) throw new RuntimeException("No se pudo abrir el menú Export");
            utils.shortDelay();

            log.info("[Web of Science] Seleccionando opción BibTeX");
            boolean chooseBib = utils.clickAny(d, 10,
                    By.id("exportToBibtexButton"),
                    By.xpath("//button[@role='menuitem' and @id='exportToBibtexButton']")
            );
            if (!chooseBib) throw new RuntimeException("No se pudo seleccionar BibTeX en el menú Export");
            utils.humanDelay();

            // 4) En el modal: seleccionar 'Records from' (fromRange) y el contenido 'Author, Title, Source, Abstract'
            log.info("[Web of Science] Asegurando selección 'Records from'");
            ensureFromRangeSelected(d);
            utils.shortDelay();

            // Abrir el dropdown de contenido (muestra inicialmente 'Author, Title, Source')
            log.info("[Web of Science] Abriendo dropdown de contenido (Author, Title, Source)");
            boolean openContent = utils.clickAny(d, 10,
                    By.xpath("//button[contains(@class,'dropdown') and @aria-haspopup='listbox' and contains(@aria-label,'Author, Title, Source')]")
            );
            if (!openContent) {
                // Fallback: buscar por texto visible
                log.debug("[Web of Science] Fallback: abriendo dropdown por texto visible");
                openContent = utils.clickAny(d, 8,
                        By.xpath("//button[contains(@class,'dropdown')]//span[contains(@class,'dropdown-text') and contains(.,'Author, Title, Source')]/ancestor::button")
                );
            }
            if (openContent) {
                log.info("[Web of Science] Dropdown de contenido abierto");
            } else {
                log.warn("[Web of Science] No se pudo abrir el dropdown de contenido");
            }
            utils.shortDelay();

            // Elegir 'Author, Title, Source, Abstract'
            log.info("[Web of Science] Seleccionando 'Author, Title, Source, Abstract'");
            boolean pickAbstract = utils.clickAny(d, 10,
                    By.xpath("//div[@id='global-select']//div[@role='menuitem' and (@aria-label='Author, Title, Source, Abstract' or .//span[normalize-space(text())='Author, Title, Source, Abstract'])]")
            );
            if (!pickAbstract) {
                // Fallback: buscar opción por texto plano en cualquier panel abierto
                log.debug("[Web of Science] Fallback: seleccionando opción por texto plano en panel abierto");
                pickAbstract = utils.clickAny(d, 6,
                        By.xpath("//div[@role='menuitem']//span[normalize-space(text())='Author, Title, Source, Abstract']/parent::div")
                );
            }
            if (pickAbstract) {
                log.info("[Web of Science] Opción 'Author, Title, Source, Abstract' seleccionada");
            } else {
                log.warn("[Web of Science] No se pudo seleccionar 'Author, Title, Source, Abstract'");
            }
            utils.shortDelay();

            // 5) Exportar
            log.info("[Web of Science] Confirmando exportación");
            boolean confirm = utils.clickAny(d, 15,
                    By.id("exportButton"),
                    By.xpath("//button[@id='exportButton' or (contains(@class,'mat-mdc-unelevated-button') and .//span[contains(.,'Export')])]"));
            if (!confirm) throw new RuntimeException("No se pudo confirmar la exportación en WOS");
            utils.downloadDelay();

            // Esperar específicamente savedrecs.bib para evitar renombrar otros archivos
            try {
                log.info("[Web of Science] Esperando archivo 'savedrecs.bib' en {} para renombrar", cfg.getDownloadDirectory());
                var file = dl.waitAndRenameSpecificBib(cfg.getDownloadDirectory(), "savedrecs.bib", "WebOfScience");
                if (file == null) {
                    log.warn("[Web of Science] No se encontró savedrecs.bib en el tiempo esperado");
                } else {
                    log.info("[Web of Science] Archivo renombrado correctamente: {}", file.getAbsolutePath());
                }
            } catch (Exception ex) {
                log.warn("[Web of Science] Error esperando/renombrando savedrecs.bib: {}", ex.getMessage());
            }

            // Asegurar que no queden archivos temporales de descarga antes de continuar (ej: .crdownload, .part, .tmp, .download)
            try {
                Path downloadDir = Paths.get(cfg.getDownloadDirectory());
                if (Files.exists(downloadDir)) {
                    log.info("[Web of Science] Verificando archivos temporales en {}", cfg.getDownloadDirectory());
                    int maxWaitSeconds = 120;
                    int waited = 0;
                    boolean hasTemp;
                    do {
                        hasTemp = false;
                        try (DirectoryStream<Path> ds = Files.newDirectoryStream(downloadDir)) {
                            for (Path p : ds) {
                                String name = p.getFileName().toString().toLowerCase();
                                if (name.endsWith(".crdownload") || name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".download")) {
                                    hasTemp = true;
                                    break;
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("[Web of Science] Error comprobando carpeta de descargas: {}", ex.getMessage());
                        }
                        if (hasTemp) {
                            utils.humanDelay(); // espera humana breve (aprox. 1s)
                            waited++;
                        }
                    } while (hasTemp && waited < maxWaitSeconds);
                    if (hasTemp) {
                        log.warn("[Web of Science] Tiempo de espera de descarga excedido; aún hay temporales en {}", cfg.getDownloadDirectory());
                    } else {
                        log.debug("[Web of Science] Descarga completada: no hay archivos temporales");
                    }
                } else {
                    log.debug("[Web of Science] Directorio de descargas no existe: {}", cfg.getDownloadDirectory());
                }
            } catch (Exception ex) {
                log.debug("[Web of Science] Fallo comprobando finalización de descarga: {}", ex.getMessage());
            }

            // 6) Cerrar sesión solo después de tener el archivo descargado
            try {
                log.info("[Web of Science] Intentando cerrar sesión");
                boolean openedAccountMenu = utils.clickAny(d, 8,
                        By.cssSelector("button.wos-login-account.mat-mdc-menu-trigger"),
                        By.cssSelector("button[data-ta='wos-header-user_name']"),
                        By.xpath("//button[@aria-haspopup='menu' and contains(@aria-label,'Account options')]")
                );
                if (openedAccountMenu) {
                    utils.shortDelay();
                    boolean clickedLogout = utils.clickAny(d, 8,
                            By.cssSelector("a[routerlink='/my/sign-out']"),
                            By.xpath("//a[contains(@href,'/wos/my/sign-out')]") ,
                            By.xpath("//a[@role='menuitem']//span[normalize-space(text())='End session and log out']/ancestor::a")
                    );
                    if (clickedLogout) {
                        // Esperar redirección o confirmación de cierre de sesión
                        for (int i = 0; i < 10; i++) {
                            utils.humanDelay();
                            try {
                                String u = d.getCurrentUrl();
                                log.debug("[Web of Science] URL tras logout intento {}: {}", i + 1, u);
                                if (u != null && (u.contains("sign-out") || u.contains("login") || u.contains("access.clarivate"))) break;
                            } catch (Exception ignored) {}
                        }
                        log.info("[Web of Science] Sesión cerrada (logout)");
                    } else {
                        log.debug("[Web of Science] No se encontró la opción 'End session and log out'");
                    }
                } else {
                    log.debug("[Web of Science] No se pudo abrir el menú de cuenta para cerrar sesión");
                }
            } catch (Exception ex) {
                log.debug("[Web of Science] Fallo al intentar cerrar sesión: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("[Web of Science] Fallo exportando: {}", e.getMessage());
        }
        log.info("[Web of Science] Fin");
    }

    private void performClarivateLogin(WebDriver d, String email, String password) {
        try {
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.info("[Web of Science] No hay credenciales configuradas (automation.email/automation.password); se espera login manual si es requerido.");
                return;
            }
            // Campo email
            log.info("[Web of Science] Completando login Clarivate: email");
            WebElement emailInput = utils.findAnyVisible(d, 20,
                    By.id("mat-input-0"),
                    By.name("email"),
                    By.cssSelector("input[formcontrolname='email']"),
                    By.cssSelector("input[type='email']")
            );
            if (emailInput != null) { try { emailInput.clear(); } catch (Exception ignored) {} emailInput.sendKeys(email); utils.shortDelay(); }

            // Campo password
            log.info("[Web of Science] Completando login Clarivate: password");
            WebElement passInput = utils.findAnyVisible(d, 20,
                    By.id("mat-input-1"),
                    By.name("password"),
                    By.cssSelector("input[formcontrolname='password']"),
                    By.cssSelector("input[type='password']")
            );
            if (passInput != null) { try { passInput.clear(); } catch (Exception ignored) {} passInput.sendKeys(password); utils.shortDelay(); }

            // Botón Sign in
            log.info("[Web of Science] Pulsando 'Sign in'");
            boolean clicked = utils.clickAny(d, 10,
                    By.id("signIn-btn"),
                    By.name("login-btn"),
                    By.xpath("//button[@type='submit' and (contains(.,'Sign in') or contains(.,'Sign In'))]")
            );
            if (!clicked) log.debug("[Web of Science] No se pudo pulsar el botón Sign in (Clarivate)");

            // Esperar redirección a Web of Science
            for (int i = 0; i < 30; i++) {
                utils.humanDelay();
                try {
                    String u = d.getCurrentUrl();
                    log.debug("[Web of Science] URL tras login intento {}: {}", i + 1, u);
                    if (u != null && (u.contains("webofscience.com") || u.contains("webofknowledge.com"))) break;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("[Web of Science] Fallo durante login de Clarivate: {}", e.getMessage());
        }
    }

    // Asegurar que el radiobutton 'Records from:' (fromRange) quede seleccionado
    private void ensureFromRangeSelected(WebDriver d) {
        try {
            log.info("[Web of Science] Asegurando radio 'Records from' (fromRange)");
            // 1) Intentar con el input nativo
            WebElement input = null;
            try { input = d.findElement(By.id("radio3-input")); } catch (Exception ignored) {}
            if (input == null) {
                try { input = d.findElement(By.xpath("//input[@type='radio' and @name='outputMethodType' and @value='fromRange']")); } catch (Exception ignored) {}
            }
            if (input != null) {
                utils.scrollIntoView(d, input);
                if (!input.isSelected()) {
                    try { input.click(); log.debug("[Web of Science] Click en input 'fromRange'"); } catch (Exception e) { utils.clickJS(d, input); log.debug("[Web of Science] Click JS en input 'fromRange'"); }
                    utils.shortDelay();
                }
                if (input.isSelected()) { log.debug("[Web of Science] 'Records from' seleccionado vía input"); return; }
            }

            // 2) Intentar clic en el label asociado
            try {
                WebElement label = d.findElement(By.xpath("//label[@for='radio3-input' and contains(.,'Records from')]"));
                utils.scrollIntoView(d, label);
                try { label.click(); log.debug("[Web of Science] Click en label 'Records from'"); } catch (Exception e) { utils.clickJS(d, label); log.debug("[Web of Science] Click JS en label 'Records from'"); }
                utils.shortDelay();
                // Validar input de nuevo
                try { if (input == null) input = d.findElement(By.id("radio3-input")); } catch (Exception ignored) {}
                if (input != null && input.isSelected()) { log.debug("[Web of Science] 'Records from' seleccionado vía label"); return; }
            } catch (Exception ignored) {}

            // 3) Intentar clic en el wrapper del mat-radio-button
            try {
                WebElement wrapper = d.findElement(By.id("radio3"));
                utils.scrollIntoView(d, wrapper);
                try { wrapper.click(); log.debug("[Web of Science] Click en wrapper 'radio3'"); } catch (Exception e) { utils.clickJS(d, wrapper); log.debug("[Web of Science] Click JS en wrapper 'radio3'"); }
                utils.shortDelay();
                // Validar por estado del input o clase checked
                boolean checked = false;
                try { if (input == null) input = d.findElement(By.id("radio3-input")); } catch (Exception ignored) {}
                if (input != null && input.isSelected()) checked = true;
                try {
                    String cls = wrapper.getDomAttribute("class");
                    if (cls != null && cls.contains("mat-mdc-radio-checked")) checked = true;
                } catch (Exception ignored) {}
                if (checked) { log.debug("[Web of Science] 'Records from' seleccionado vía wrapper"); return; }
            } catch (Exception ignored) {}

            // 4) Forzar por JavaScript como último recurso
            try {
                WebElement jsInput = input;
                if (jsInput == null) jsInput = d.findElement(By.xpath("//input[@type='radio' and @name='outputMethodType' and @value='fromRange']"));
                ((JavascriptExecutor) d).executeScript(
                        "arguments[0].checked = true; arguments[0].setAttribute('aria-checked','true'); arguments[0].dispatchEvent(new Event('input',{bubbles:true})); arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                        jsInput);
                utils.shortDelay();
                log.debug("[Web of Science] 'Records from' forzado por JS");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("[Web of Science] No fue posible asegurar 'Records from': {}", e.getMessage());
        }
    }
}
