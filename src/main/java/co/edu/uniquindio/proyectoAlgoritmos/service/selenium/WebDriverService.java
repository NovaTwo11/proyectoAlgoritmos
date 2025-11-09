package co.edu.uniquindio.proyectoAlgoritmos.service.selenium;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebDriverService {

    private final SeleniumConfig config;

    public WebDriver createChromeDriver() {
        // 1) Preparar carpeta de descargas
        File downloadDir = new File(config.getDownloadDirectory());
        if (!downloadDir.exists()) {
            boolean ok = downloadDir.mkdirs();
            log.info("Directorio de descargas: {} (creado={})", downloadDir.getAbsolutePath(), ok);
        }

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir.getAbsolutePath());
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("safebrowsing.enabled", true);
        prefs.put("safebrowsing.disable_download_protection", true);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("plugins.always_open_pdf_externally", true);
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        // 2) ChromeOptions seguros para server (Linux, sin GUI)
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);

        // Flags comunes
        List<String> args = new ArrayList<>(Arrays.asList(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--lang=es-ES",
                "--disable-infobars",
                "--disable-blink-features=AutomationControlled",
                "--disable-features=PasswordManagerOnboarding,PasswordManagerRedesign",
                "--remote-debugging-port=0"
        ));

        // Headless en servidores (usa el flag moderno). Si prefieres forzarlo siempre, pon true.
        if (config.isHeadless()) {
            args.add("--headless=new");
            args.add("--window-size=1920,1080"); // en headless no hay maximize
        } else {
            args.add("--start-maximized");
        }

        // Proxy DIRECT si se pide en config
        if (config.isForceDirect()) {
            try {
                Proxy direct = new Proxy();
                direct.setProxyType(Proxy.ProxyType.DIRECT);
                options.setCapability(CapabilityType.PROXY, direct);
                args.add("--no-proxy-server");
                args.add("--proxy-server=direct://");
                args.add("--proxy-bypass-list=<-loopback>");
                log.info("Chrome configurado en modo DIRECT (sin proxy)");
            } catch (Exception e) {
                log.warn("No se pudo configurar Proxy DIRECT: {}", e.getMessage());
            }
        }

        // Perfil: persistente (Linux) o temporal
        try {
            if (config.isPersistentProfile()) {
                // En Linux, el user-data-dir por defecto está bajo ~/.config/google-chrome
                String userDataDir = Paths.get(System.getProperty("user.home"),
                        ".config", "google-chrome", "auto-scrape-profile").toString();
                args.add("--user-data-dir=" + userDataDir);
                args.add("--profile-directory=Default");
                log.info("Usando perfil persistente Linux: {}", userDataDir);
            } else {
                Path tempProfile = Files.createTempDirectory("chrome-profile-" + UUID.randomUUID());
                args.add("--user-data-dir=" + tempProfile.toAbsolutePath());
                args.add("--profile-directory=Default");
                log.info("Usando perfil temporal: {}", tempProfile);
            }
        } catch (Exception e) {
            log.warn("No se pudo preparar el directorio de perfil de Chrome: {}", e.getMessage());
        }

        // Si el binario está en una ruta no estándar, puedes fijarlo con CHROME_BIN
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isBlank()) {
            options.setBinary(chromeBin);
            log.info("Usando binario de Chrome desde CHROME_BIN: {}", chromeBin);
        }
        options.addArguments(args);

        // 3) Asegurar log de ChromeDriver para depurar si algo falla al iniciar Chrome
        File driverLog = ensureLogFile();
        ChromeDriverService service = new ChromeDriverService.Builder()
                .withVerbose(true)
                .withLogFile(driverLog)
                .build();

        // 4) Resolver driver con WebDriverManager
        WebDriverManager.chromedriver().setup();

        // 5) Crear driver
        ChromeDriver driver = new ChromeDriver(service, options);

        // 6) "Stealth" básico y descargas por CDP
        applyStealth(driver);
        enableDownloadsViaCDP(driver, downloadDir.getAbsolutePath());

        return driver;
    }

    // Habilita descargas mediante CDP (Browser.setDownloadBehavior o fallback a Page.setDownloadBehavior).
    private void enableDownloadsViaCDP(WebDriver driver, String downloadDir) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("behavior", "allow");
            params.put("downloadPath", downloadDir);
            params.put("eventsEnabled", true);
            ((ChromeDriver) driver).executeCdpCommand("Browser.setDownloadBehavior", params);
            log.info("CDP Browser.setDownloadBehavior aplicado (downloadPath={})", downloadDir);
        } catch (Exception e) {
            log.warn("Fallo Browser.setDownloadBehavior: {} - intentando Page.setDownloadBehavior", e.getMessage());
            try {
                Map<String, Object> params2 = new HashMap<>();
                params2.put("behavior", "allow");
                params2.put("downloadPath", downloadDir);
                ((ChromeDriver) driver).executeCdpCommand("Page.setDownloadBehavior", params2);
                log.info("CDP Page.setDownloadBehavior aplicado (downloadPath={})", downloadDir);
            } catch (Exception ignore) {
                log.warn("No fue posible configurar el comportamiento de descarga vía CDP. Se usarán prefs de Chrome.");
            }
        }
    }

    // Oculta navigator.webdriver y propiedades típicas para reducir detección básica.
    private void applyStealth(ChromeDriver driver) {
        try {
            Map<String, Object> params = new HashMap<>();
            String script = String.join("\n",
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });",
                    "window.chrome = { runtime: {} };",
                    "Object.defineProperty(navigator, 'languages', { get: () => ['es-ES','es','en-US'] });",
                    "Object.defineProperty(navigator, 'platform', { get: () => 'Linux x86_64' });",
                    "Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5] });"
            );
            params.put("source", "(() => {" + script + "})()");
            driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
            log.debug("Script stealth inyectado en nuevas páginas");
        } catch (Exception e) {
            log.warn("No se pudo inyectar script stealth: {}", e.getMessage());
        }
    }

    private File ensureLogFile() {
        try {
            Path logDir = Paths.get("/var/log/miapp");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            return logDir.resolve("chromedriver.log").toFile();
        } catch (Exception e) {
            log.warn("No se pudo preparar /var/log/miapp, usando /tmp para logs: {}", e.getMessage());
            return Paths.get("/tmp/chromedriver.log").toFile();
        }
    }
}
