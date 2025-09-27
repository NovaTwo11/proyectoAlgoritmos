package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ProcessingStatus;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataUnificationServiceTest {

    @Mock
    private DataDownloaderService downloaderService;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @Mock
    private FileProcessingService fileProcessingService;

    @Mock
    private CsvUtils csvUtils;

    @InjectMocks
    private DataUnificationService dataUnificationService;

    private List<ScientificRecord> sampleRecords;

    @BeforeEach
    void setUp() {
        sampleRecords = List.of(
                ScientificRecord.builder()
                        .title("AI in Education")
                        .authors(List.of("Smith, J."))
                        .source(DataSource.ACM.toString())
                        .build(),

                ScientificRecord.builder()
                        .title("Machine Learning Applications")
                        .authors(List.of("Johnson, M."))
                        .source(DataSource.SAGE.toString())
                        .build()
        );
    }

    @Test
    void testProcessAndUnifyData_Success() throws Exception {
        // Given
        String searchQuery = "generative AI";

        when(downloaderService.downloadFromSource(eq(DataSource.ACM), eq(searchQuery)))
                .thenReturn(sampleRecords.subList(0, 1));
        when(downloaderService.downloadFromSource(eq(DataSource.SAGE), eq(searchQuery)))
                .thenReturn(sampleRecords.subList(1, 2));

        when(duplicateDetectionService.detectDuplicates(anyList()))
                .thenReturn(Map.of());
        when(duplicateDetectionService.getUniqueRecords(anyList()))
                .thenReturn(sampleRecords);

        when(fileProcessingService.saveUnifiedRecords(anyList(), anyString()))
                .thenReturn("/path/to/unified.csv");
        when(fileProcessingService.saveDuplicateRecords(anyMap(), anyString()))
                .thenReturn("/path/to/duplicates.csv");

        // When
        CompletableFuture<ProcessingResultDto> future =
                dataUnificationService.processAndUnifyData(searchQuery);
        ProcessingResultDto result = future.get();

        // Then
        assertNotNull(result);
        assertEquals(ProcessingStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getStats());
        assertEquals(2, result.getStats().getTotalRecordsProcessed());

        verify(downloaderService, times(2)).downloadFromSource(any(), eq(searchQuery));
        verify(duplicateDetectionService).detectDuplicates(anyList());
        verify(fileProcessingService).saveUnifiedRecords(anyList(), anyString());
    }

    @Test
    void testProcessAndUnifyData_WithError() throws Exception {
        // Given
        String searchQuery = "test query";

        when(downloaderService.downloadFromSource(any(), anyString()))
                .thenThrow(new RuntimeException("Download error"));

        // When
        CompletableFuture<ProcessingResultDto> future =
                dataUnificationService.processAndUnifyData(searchQuery);
        ProcessingResultDto result = future.get();

        // Then
        assertNotNull(result);
        // En lugar de FALLAR, aquí ahora validamos que no haya registros y esté completo
        assertEquals(ProcessingStatus.COMPLETED, result.getStatus());
        assertEquals(0, result.getStats().getTotalRecordsProcessed());
    }
}