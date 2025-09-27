package co.edu.uniquindio.proyectoAlgoritmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 segundos
        factory.setReadTimeout(30000);    // 30 segundos

        RestTemplate restTemplate = new RestTemplate(factory);

        // Agregar User-Agent para evitar bloqueos
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "ProyectoAlgoritmos/1.0 (Universidad del Quindío)");
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}