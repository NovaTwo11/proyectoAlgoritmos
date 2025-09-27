package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.mapper.FieldMapper;
import java.io.*;
import java.util.*;

/**
 * Lector genérico de archivos CSV para datasets científicos
 */
public class CSVDatasetReader implements DatasetReader {

    private final FieldMapper fieldMapper;
    private final String sourceName;
    private String delimiter = ",";
    private boolean hasHeader = true;

    public CSVDatasetReader(FieldMapper fieldMapper, String sourceName) {
        this.fieldMapper = fieldMapper;
        this.sourceName = sourceName;
    }

    public CSVDatasetReader(FieldMapper fieldMapper, String sourceName, String delimiter) {
        this(fieldMapper, sourceName);
        this.delimiter = delimiter;
    }

    @Override
    public List<ScientificRecord> readDataset(String filePath) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String[] headers = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1 && hasHeader) {
                    headers = parseLine(line);
                    continue;
                }

                try {
                    String[] values = parseLine(line);
                    if (values.length > 0 && !isEmptyLine(values)) {
                        ScientificRecord record = mapToScientificRecord(headers, values);
                        if (record != null) {
                            record.setSource(sourceName);
                            records.add(record);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error procesando línea " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        System.out.println("Leídos " + records.size() + " registros de " + filePath);
        return records;
    }

    /**
     * Parsea una línea CSV considerando comillas y delimitadores
     */
    private String[] parseLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        result.add(currentField.toString().trim());
        return result.toArray(new String[0]);
    }

    private boolean isEmptyLine(String[] values) {
        return Arrays.stream(values).allMatch(String::isEmpty);
    }

    private ScientificRecord mapToScientificRecord(String[] headers, String[] values) {
        if (headers == null || values == null || values.length == 0) {
            return null;
        }

        Map<String, String> fieldMap = new HashMap<>();

        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
            if (headers[i] != null && values[i] != null) {
                fieldMap.put(headers[i].trim(), values[i].trim());
            }
        }

        return fieldMapper.mapToScientificRecord(fieldMap);
    }

    @Override
    public boolean isCompatible(String filePath) {
        return filePath.toLowerCase().endsWith(".csv");
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    // Getters y Setters
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }
}