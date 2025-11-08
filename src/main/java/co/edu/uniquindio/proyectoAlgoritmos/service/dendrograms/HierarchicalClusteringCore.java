package co.edu.uniquindio.proyectoAlgoritmos.service.dendrograms;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HierarchicalClusteringCore {

    public static class Merge {
        public final int left; public final int right; public final double height; public final int size;
        public Merge(int left, int right, double height, int size) { this.left=left; this.right=right; this.height=height; this.size=size; }
    }

    public enum Linkage { SINGLE, COMPLETE, AVERAGE, WARD }

    public List<Merge> agglomerate(double[][] dist, Linkage linkage) {
        int n = dist.length;
        if (n == 0) return List.of();
        boolean[] alive = new boolean[2*n];
        Arrays.fill(alive, false);
        for (int i=0;i<n;i++) alive[i]=true;
        @SuppressWarnings("unchecked")
        List<Set<Integer>> clusters = new ArrayList<>(2*n);
        for (int i=0;i<2*n;i++) clusters.add(null);
        for (int i=0;i<n;i++) clusters.set(i, new LinkedHashSet<>(List.of(i)));
        int[] size = new int[2*n]; Arrays.fill(size,0);
        for (int i=0;i<n;i++) size[i]=1;
        double[][] cdist = new double[2*n][2*n];
        for (int i=0;i<n;i++) System.arraycopy(dist[i], 0, cdist[i], 0, n);

        List<Merge> merges = new ArrayList<>(Math.max(0, n-1));
        int next = n;
        for (int step=0; step<n-1; step++) {
            double best = Double.POSITIVE_INFINITY; int ai=-1, bi=-1;
            for (int i=0;i<next;i++) if (alive[i]) {
                for (int j=i+1;j<next;j++) if (alive[j]) {
                    double d = cdist[i][j];
                    if (d < best) { best=d; ai=i; bi=j; }
                }
            }
            if (ai==-1) break;
            Set<Integer> merged = new LinkedHashSet<>(clusters.get(ai)); merged.addAll(clusters.get(bi));
            clusters.set(next, merged);
            alive[ai]=false; alive[bi]=false; alive[next]=true;
            size[next] = size[ai] + size[bi];
            merges.add(new Merge(ai, bi, best, size[next]));

            for (int k=0;k<next;k++) if (alive[k]) {
                double d;
                switch (linkage) {
                    case SINGLE -> {
                        // enlace simple: mínima distancia a cualquiera de los miembros
                        d = Math.min(cdist[ai][k], cdist[bi][k]);
                    }
                    case COMPLETE -> {
                        // enlace completo: máxima distancia a cualquiera de los miembros
                        d = Math.max(cdist[ai][k], cdist[bi][k]);
                    }
                    case AVERAGE -> {
                        // promedio ponderado por tamaño (UPGMA)
                        d = (cdist[ai][k]*size[ai] + cdist[bi][k]*size[bi]) / (size[ai]+size[bi]);
                    }
                    case WARD -> {
                        // Ward: incremento de varianza ≈ fórmula de Lance–Williams para Ward (usando distancias euclidianas^2)
                        double da = cdist[ai][k];
                        double db = cdist[bi][k];
                        double dab = best;
                        double sa = size[ai], sb = size[bi], sk = size[k];
                        double num = (sa+sk)*da*da + (sb+sk)*db*db - sk*dab*dab;
                        double den = (sa+sb+sk);
                        double d2 = Math.max(0.0, num / den);
                        d = Math.sqrt(d2);
                    }
                    default -> d = (cdist[ai][k] + cdist[bi][k]) * 0.5; // fallback seguro
                }
                cdist[next][k]=d; cdist[k][next]=d;
            }
            next++;
        }
        return merges;
    }
}
