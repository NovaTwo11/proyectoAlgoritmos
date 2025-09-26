package co.edu.uniquindio.proyectoAlgoritmos.util;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class CsvUtils {

    private static final String[] HEADERS = {
            "ID", "Title", "Authors", "Abstract", "Keywords", "Journal",
            "Publication_Date", "Year", "DOI", "URL", "Source", "Country", "Language"
    };

    public List<ScientificRecord> readRecordsFromCsv(String filePath) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();

        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord csvRecord : csvParser) {
                try {
                    ScientificRecord record = mapCsvRecordToScientificRecord(csvRecord);
                    records.add(record);
                } catch (Exception e) {
                    log.warn("Error procesando registro en línea {}: {}", csvRecord.getRecordNumber(), e.getMessage());
                }
            }
        }

        log.info("Leídos {} registros desde {}", records.size(), filePath);
        return records;
    }

    public void writeRecordsToCsv(List<ScientificRecord> records, String filePath) throws IOException {
        try (Writer writer = new FileWriter(filePath);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(HEADERS))) {

            for (ScientificRecord record : records) {
                csvPrinter.printRecord(
                        record.getId(),
                        record.getTitle(),
                        joinList(record.getAuthors()),
                        record.getAbstractText(),
                        joinList(record.getKeywords()),
                        record.getJournal(),
                        record.getPublicationDate() != null ? record.getPublicationDate().toString() : "",
                        record.getYear(),
                        record.getDoi(),
                        record.getUrl(),
                        record.getSource() != null ? record.getSource().name() : "",
                        record.getCountry(),
                        record.getLanguage()
                );
            }
        }

        log.info("Escritos {} registros en {}", records.size(), filePath);
    }

    private ScientificRecord mapCsvRecordToScientificRecord(CSVRecord csvRecord) {
        return ScientificRecord.builder()
                .id(getValueOrDefault(csvRecord, "ID", ""))
                .title(getValueOrDefault(csvRecord, "Title", ""))
                .authors(parseList(getValueOrDefault(csvRecord, "Authors", "")))
                .abstractText(getValueOrDefault(csvRecord, "Abstract", ""))
                .keywords(parseList(getValueOrDefault(csvRecord, "Keywords", "")))
                .journal(getValueOrDefault(csvRecord, "Journal", ""))
                .publicationDate(parseDate(getValueOrDefault(csvRecord, "Publication_Date", "")))
                .year(parseInteger(getValueOrDefault(csvRecord, "Year", "")))
                .doi(getValueOrDefault(csvRecord, "DOI", ""))
                .url(getValueOrDefault(csvRecord, "URL", ""))
                .source(parseDataSource(getValueOrDefault(csvRecord, "Source", "")))
                .country(getValueOrDefault(csvRecord, "Country", ""))
                .language(getValueOrDefault(csvRecord, "Language", ""))
                .build();
    }

    private String getValueOrDefault(CSVRecord record, String header, String defaultValue) {
        try {
            return record.isSet(header) ? record.get(header) : defaultValue;
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(value.split(";"));
    }

    private String joinList(List<String> list) {
        return list != null ? String.join(";", list) : "";
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException e2) {
                log.warn("No se pudo parsear la fecha: {}", dateStr);
                return null;
            }
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DataSource parseDataSource(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DataSource.UNKNOWN;
        }

        try {
            return DataSource.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DataSource.UNKNOWN;
        }
    }
}
