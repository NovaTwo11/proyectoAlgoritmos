package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;

import java.io.IOException;
import java.util.List;

/**
 * Interface para lectores que obtienen datos desde APIs REST
 */
public interface ApiDatasetReader {

    /**
     * Descarga registros desde la API usando una consulta de búsqueda
     * @param searchQuery consulta de búsqueda (ej: "generative artificial intelligence")
     * @param maxResults número máximo de resultados a obtener
     * @return Lista de registros científicos
     * @throws IOException Si hay error en la conexión o respuesta de la API
     */
    List<ScientificRecord> downloadFromApi(String searchQuery, int maxResults) throws IOException;

    /**
     * Retorna el nombre de la fuente de datos que maneja este lector
     * @return Nombre de la fuente (ej: "DBLP", "OpenAlex")
     */
    String getSourceName();

    /**
     * Verifica si la API está disponible
     * @return true si la API responde correctamente
     */
    boolean isApiAvailable();
}