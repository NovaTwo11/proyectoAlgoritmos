package co.edu.uniquindio.proyectoAlgoritmos.util;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class StringSimilarityUtils {

    /**
     * Calcula la similitud de Jaccard entre dos cadenas
     */
    public double calculateJaccardSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equals(str2)) return 1.0;

        Set<String> tokens1 = tokenize(str1.toLowerCase());
        Set<String> tokens2 = tokenize(str2.toLowerCase());

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Calcula la distancia de Levenshtein normalizada
     */
    public double calculateLevenshteinSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equals(str2)) return 1.0;

        int distance = levenshteinDistance(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());

        return maxLength == 0 ? 1.0 : 1.0 - (double) distance / maxLength;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z0-9]", "");
            if (!cleanWord.isEmpty()) {
                tokens.add(cleanWord);
            }
        }
        return tokens;
    }

    private int levenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[str1.length()][str2.length()];
    }
}