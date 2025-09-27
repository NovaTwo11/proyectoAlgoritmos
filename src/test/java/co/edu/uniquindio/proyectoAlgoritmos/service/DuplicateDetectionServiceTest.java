package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.StringSimilarityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private StringSimilarityUtils similarityUtils;

    @InjectMocks
    private DuplicateDetectionService duplicateDetectionService;

    private List<ScientificRecord> testRecords;

    @BeforeEach
    void setUp() {
        testRecords = List.of(
                ScientificRecord.builder().title("Generative AI in Education").source(DataSource.ACM.toString()).build(),
                ScientificRecord.builder().title("Generative AI in Education").source(DataSource.SAGE.toString()).build(),
                ScientificRecord.builder().title("Machine Learning Applications").source(DataSource.ACM.toString()).build()
        );

        // Solo stubeo lo que sé que voy a necesitar
        when(similarityUtils.calculateJaccardSimilarity("Generative AI in Education", "Generative AI in Education"))
                .thenReturn(1.0);
        when(similarityUtils.calculateJaccardSimilarity("Generative AI in Education", "Machine Learning Applications"))
                .thenReturn(0.2);
        when(similarityUtils.calculateJaccardSimilarity("Machine Learning Applications", "Generative AI in Education"))
                .thenReturn(0.2);
    }

    @Test
    void testDetectDuplicates_ShouldFindExactDuplicates() {
        Map<String, List<ScientificRecord>> duplicates =
                duplicateDetectionService.detectDuplicates(testRecords);

        assertFalse(duplicates.isEmpty(), "Debería detectar al menos un grupo de duplicados");
        assertTrue(duplicates.values().stream().anyMatch(group -> group.size() >= 2),
                "El grupo de duplicados debe tener al menos 2 registros");
    }

    @Test
    void testGetUniqueRecords_ShouldReturnCorrectCount() {
        List<ScientificRecord> uniqueRecords =
                duplicateDetectionService.getUniqueRecords(testRecords);

        // 3 registros - 1 duplicado real = 2 únicos esperados
        assertEquals(2, uniqueRecords.size(),
                "Después de eliminar duplicados deben quedar 2 registros únicos");
    }
}