package co.edu.uniquindio.proyectoAlgoritmos.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringSimilarityUtilsTest {

    private StringSimilarityUtils similarityUtils;

    @BeforeEach
    void setUp() {
        similarityUtils = new StringSimilarityUtils();
    }

    @Test
    void testCalculateJaccardSimilarity_IdenticalStrings() {
        // Given
        String str1 = "generative artificial intelligence";
        String str2 = "generative artificial intelligence";

        // When
        double similarity = similarityUtils.calculateJaccardSimilarity(str1, str2);

        // Then
        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void testCalculateJaccardSimilarity_DifferentStrings() {
        // Given
        String str1 = "generative artificial intelligence";
        String str2 = "machine learning applications";

        // When
        double similarity = similarityUtils.calculateJaccardSimilarity(str1, str2);

        // Then
        assertTrue(similarity >= 0.0 && similarity <= 1.0);
        assertTrue(similarity < 0.5); // Deberían ser bastante diferentes
    }

    @Test
    void testCalculateJaccardSimilarity_NullStrings() {
        // When & Then
        assertEquals(0.0, similarityUtils.calculateJaccardSimilarity(null, "test"));
        assertEquals(0.0, similarityUtils.calculateJaccardSimilarity("test", null));
        assertEquals(0.0, similarityUtils.calculateJaccardSimilarity(null, null));
    }

    @Test
    void testCalculateLevenshteinSimilarity_IdenticalStrings() {
        // Given
        String str1 = "artificial intelligence";
        String str2 = "artificial intelligence";

        // When
        double similarity = similarityUtils.calculateLevenshteinSimilarity(str1, str2);

        // Then
        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void testCalculateLevenshteinSimilarity_SimilarStrings() {
        // Given
        String str1 = "artificial intelligence";
        String str2 = "artificial intellgence"; // Typo: missing 'i'

        // When
        double similarity = similarityUtils.calculateLevenshteinSimilarity(str1, str2);

        // Then
        assertTrue(similarity > 0.9); // Debería ser muy similar
    }

    @Test
    void testCalculateLevenshteinSimilarity_EmptyStrings() {
        // When & Then
        assertEquals(1.0, similarityUtils.calculateLevenshteinSimilarity("", ""));
        assertTrue(similarityUtils.calculateLevenshteinSimilarity("test", "") < 1.0);
    }
}
