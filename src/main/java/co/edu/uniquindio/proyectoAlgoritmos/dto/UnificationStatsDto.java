package co.edu.uniquindio.proyectoAlgoritmos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnificationStatsDto {
    private int totalRecordsProcessed;
    private int uniqueRecords;
    private int duplicatesFound;
    private int recordsFromSource1;
    private int recordsFromSource2;
    private double duplicatePercentage;
}