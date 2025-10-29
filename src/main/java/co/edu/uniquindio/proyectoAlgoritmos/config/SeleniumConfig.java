package co.edu.uniquindio.proyectoAlgoritmos.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class SeleniumConfig {

    @Value("${automation.email}")
    private String academicEmail;

    @Value("${automation.password}")
    private String password;

    @Value("${automation.search.query}")
    private String searchQuery;

    @Value("${automation.download.directory}")
    private String downloadDirectory;

    @Value("${automation.portal.url}")
    private String portalUrl;

    @Value("${automation.headless:false}")
    private boolean headless;

    @Value("${automation.max.pages}")
    private int maxPages;

    @Value("${automation.chrome.forceDirect:false}")
    private boolean forceDirect;

    @Value("${automation.chrome.persistentProfile:true}")
    private boolean persistentProfile;

}
