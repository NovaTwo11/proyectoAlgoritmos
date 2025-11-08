package co.edu.uniquindio.proyectoAlgoritmos.util;

import co.edu.uniquindio.proyectoAlgoritmos.service.dendrograms.HierarchicalClusteringCore;

import java.util.*;

/**
 * Métricas para evaluar dendrogramas.
 * Implementa el Cophenetic Correlation Coefficient (CCC):
 * correlación de Pearson entre las distancias originales y las distancias cophenéticas
 * inducidas por el dendrograma.
 */
public final class DendroMetrics {

    private DendroMetrics() {}

    /**
     * Calcula el coeficiente de correlación cophenética (CCC).
     *
     * @param dist   matriz de distancias original (simétrica, diagonal 0). Tamaño n×n.
     * @param merges lista de fusiones producidas por el aglomerativo; se asume orden cronológico.
     * @return valor en [-1, 1]. Si no es posible calcular, retorna 0.0.
     */
    public static double copheneticCorrelation(double[][] dist, List<HierarchicalClusteringCore.Merge> merges) {
        if (dist == null || dist.length < 2) return 0.0;
        final int n = dist.length;

        // Construir matriz cophenética (n×n) a partir de las fusiones
        double[][] coph = copheneticMatrix(n, merges);
        if (coph == null) return 0.0;

        // Extraer el triángulo superior (i<j) a vectores y calcular correlación de Pearson
        final int m = n * (n - 1) / 2;
        double[] a = new double[m];
        double[] b = new double[m];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (dist[i] == null || dist[i].length != n) return 0.0;
            for (int j = i + 1; j < n; j++) {
                double dij = dist[i][j];
                double cij = coph[i][j];
                if (!isFinite(dij) || !isFinite(cij)) continue; // ignora pares no válidos
                a[idx] = dij;
                b[idx] = cij;
                idx++;
            }
        }
        if (idx < 2) return 0.0; // no hay suficientes pares

        // Si hubo pares filtrados, recortar
        if (idx != m) {
            a = Arrays.copyOf(a, idx);
            b = Arrays.copyOf(b, idx);
        }
        return pearson(a, b);
    }

    /**
     * Construye la matriz cophenética n×n: para cada par (i,j) la altura (distancia)
     * de la fusión en la que quedan en el mismo cluster por primera vez.
     */
    private static double[][] copheneticMatrix(int n, List<HierarchicalClusteringCore.Merge> merges) {
        if (n < 2 || merges == null || merges.isEmpty()) return null;

        // clusterLeaves[k] = conjunto de hojas contenidas en el cluster k.
        // Índices 0..n-1 son hojas originales; los siguientes son clusters nuevos.
        @SuppressWarnings("unchecked")
        List<Set<Integer>> clusterLeaves = new ArrayList<>(2 * n);
        for (int i = 0; i < 2 * n; i++) clusterLeaves.add(null);
        for (int i = 0; i < n; i++) clusterLeaves.set(i, new HashSet<>(List.of(i)));

        double[][] coph = new double[n][n];
        int next = n;

        for (HierarchicalClusteringCore.Merge m : merges) {
            int li = m.left, ri = m.right;
            if (li < 0 || ri < 0 || li >= clusterLeaves.size() || ri >= clusterLeaves.size()) continue;

            Set<Integer> L = clusterLeaves.get(li);
            Set<Integer> R = clusterLeaves.get(ri);
            if (L == null || R == null) continue;

            // Para todo par (i en L, j en R), su distancia cophenética es la altura de esta fusión
            double h = sanitizeHeight(m.height);
            for (int i : L) {
                for (int j : R) {
                    coph[i][j] = coph[j][i] = h;
                }
            }

            // Crear el nuevo cluster con hojas L ∪ R
            Set<Integer> merged = new HashSet<>(L.size() + R.size());
            merged.addAll(L);
            merged.addAll(R);
            clusterLeaves.set(next, merged);

            // Marcar los clusters usados como nulos para evitar reuso accidental
            clusterLeaves.set(li, null);
            clusterLeaves.set(ri, null);
            next++;
        }

        // Asegurar diagonal 0
        for (int i = 0; i < n; i++) coph[i][i] = 0.0;

        return coph;
    }

    /**
     * Correlación de Pearson entre dos vectores (mismo tamaño).
     * Devuelve 0 si varianza es cero o hay valores no finitos.
     */
    private static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return 0.0;

        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int k = 0;
        for (int i = 0; i < n; i++) {
            double xi = x[i], yi = y[i];
            if (!isFinite(xi) || !isFinite(yi)) continue;
            sx += xi; sy += yi;
            sxx += xi * xi; syy += yi * yi;
            sxy += xi * yi;
            k++;
        }
        if (k < 2) return 0.0;

        double num = k * sxy - sx * sy;
        double denPart1 = k * sxx - sx * sx;
        double denPart2 = k * syy - sy * sy;
        double den = Math.sqrt(Math.max(0.0, denPart1) * Math.max(0.0, denPart2));

        if (den == 0.0 || !isFinite(num) || !isFinite(den)) return 0.0;
        double r = num / den;
        // Clamp por seguridad numérica
        if (r > 1.0) r = 1.0;
        if (r < -1.0) r = -1.0;
        return r;
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double sanitizeHeight(double h) {
        if (!isFinite(h) || h < 0) return 0.0;
        return h;
    }
}





