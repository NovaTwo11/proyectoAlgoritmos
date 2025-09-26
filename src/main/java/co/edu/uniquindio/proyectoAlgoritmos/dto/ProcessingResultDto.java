package co.edu.uniquindio.proyectoAlgoritmos.dto;

import co.edu.uniquindio.proyectoAlgoritmos.model.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResultDto {
    private String processId;
    private ProcessingStatus status;
    private String message;
    private UnificationStatsDto stats;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String unifiedFilePath;
    private String duplicatesFilePath;
}