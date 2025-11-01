package co.edu.uniquindio.proyectoAlgoritmos.service.selenium;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebDriverService {

    private final SeleniumConfig config;

    public WebDriver createChromeDriver() {
        WebDriverManager.chromedriver().setup();

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
        // Evitar bloqueos de protección de descargas en algunos sitios
        prefs.put("safebrowsing.disable_download_protection", true);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("plugins.always_open_pdf_externally", true);
        // Evitar el aviso de guardar contraseña y el autocompletado de credenciales
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--start-maximized");
        options.addArguments("--lang=es-ES");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        // Ocultar barra de "Chrome está siendo controlado por un software automatizado" y señales básicas
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-blink-features=AutomationControlled");
        // Reducir aún más prompts relacionados con contraseñas
        options.addArguments("--disable-features=PasswordManagerOnboarding,PasswordManagerRedesign");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation", "enable-logging"));
        options.setExperimentalOption("useAutomationExtension", false);

        // Forzar conexión directa sin proxy si se configura
        if (config.isForceDirect()) {
            try {
                Proxy direct = new Proxy();
                direct.setProxyType(Proxy.ProxyType.DIRECT);
                options.setCapability(CapabilityType.PROXY, direct);
                // Flags auxiliares para Chrome
                options.addArguments("--no-proxy-server");
                options.addArguments("--proxy-server=direct://");
                options.addArguments("--proxy-bypass-list=<-loopback>");
                log.info("Chrome configurado en modo DIRECT (sin proxy)");
            } catch (Exception e) {
                log.warn("No se pudo configurar Proxy DIRECT: {}", e.getMessage());
            }
        }

        if (config.isHeadless()) options.addArguments("--headless=new");

        // Perfil: persistente o temporal
        try {
            if (config.isPersistentProfile()) {
                String userDataDir = Paths.get(System.getProperty("user.home"),
                        "AppData", "Local", "Google", "Chrome", "User Data", "auto-scrape-profile").toString();
                options.addArguments("--user-data-dir=" + userDataDir);
                log.info("Usando perfil persistente: {}", userDataDir);
            } else {
                Path tempProfile = Files.createTempDirectory("chrome-profile-" + UUID.randomUUID());
                options.addArguments("--user-data-dir=" + tempProfile.toAbsolutePath());
                options.addArguments("--profile-directory=Default");
                log.info("Usando perfil temporal: {}", tempProfile);
            }
        } catch (Exception e) {
            log.warn("No se pudo preparar el directorio de perfil de Chrome: {}", e.getMessage());
        }

        ChromeDriver driver = new ChromeDriver(options);
        // Stealth: ocultar navigator.webdriver y otros indicadores
        applyStealth(driver);
        // CDP: permitir descargas y establecer el directorio de destino para evitar cancelaciones
        enableDownloadsViaCDP(driver, downloadDir.getAbsolutePath());
        return driver;
    }

    // Habilita descargas mediante CDP de forma agnóstica a la versión (Browser/Page.setDownloadBehavior).
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
                log.warn("No fue posible configurar el comportamiento de descarga vía CDP. Se usará prefs de Chrome.");
            }
        }
    }

    private void applyStealth(ChromeDriver driver) {
        try {
            // Inyectar script para desactivar navigator.webdriver y simular propiedades comunes
            Map<String, Object> params = new HashMap<>();
            String script = String.join("\n",
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });",
                    "window.chrome = { runtime: {} };",
                    "Object.defineProperty(navigator, 'languages', { get: () => ['es-ES','es','en-US'] });",
                    "Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });",
                    "Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5] });"
            );
            params.put("source", "(() => {" + script + "})()");
            driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
            log.debug("Script stealth inyectado en nuevas páginas");
        } catch (Exception e) {
            log.warn("No se pudo inyectar script stealth: {}", e.getMessage());
        }
    }
}
