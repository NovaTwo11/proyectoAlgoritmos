package co.edu.uniquindio.proyectoAlgoritmos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SortingAlgorithmsServiceTest {

    private SortingAlgorithmsService sortingService;
    private int[] testArray;

    @BeforeEach
    void setUp() {
        sortingService = new SortingAlgorithmsService();
        testArray = new int[]{64, 34, 25, 12, 22, 11, 90, 5};
    }

    @Test
    void testTimSort() {
        int[] arr = testArray.clone();
        sortingService.timSort(arr);
        assertTrue(isSorted(arr));
    }

    @Test
    void testQuickSort() {
        int[] arr = testArray.clone();
        sortingService.quickSort(arr);
        assertTrue(isSorted(arr));
    }

    @Test
    void testHeapSort() {
        int[] arr = testArray.clone();
        sortingService.heapSort(arr);
        assertTrue(isSorted(arr));
    }

    @Test
    void testSelectionSort() {
        int[] arr = testArray.clone();
        sortingService.selectionSort(arr);
        assertTrue(isSorted(arr));
    }

    @Test
    void testRadixSort() {
        int[] arr = testArray.clone();
        sortingService.radixSort(arr);
        assertTrue(isSorted(arr));
    }

    private boolean isSorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[i - 1]) {
                return false;
            }
        }
        return true;
    }
}