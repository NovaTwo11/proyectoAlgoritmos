package co.edu.uniquindio.proyectoAlgoritmos.mapper;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import java.util.Map;

/**
 * Interface para mapear campos de diferentes fuentes a ScientificRecord
 */
public interface FieldMapper {

    /**
     * Mapea un mapa de campos a un ScientificRecord
     * @param fields Mapa con los campos del registro original
     * @return ScientificRecord mapeado
     */
    ScientificRecord mapToScientificRecord(Map<String, String> fields);

    /**
     * Retorna el nombre de la fuente que mapea
     * @return Nombre de la fuente
     */
    String getSourceName();
}