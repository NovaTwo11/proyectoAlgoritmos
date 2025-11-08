package co.edu.uniquindio.proyectoAlgoritmos.service.dendrograms;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;

@Service
public class DendrogramRendererService {

    private static final int MAX_HEIGHT = 4800; // más alto pero con OOM guard
    private static final int MIN_PX_PER_LEAF = 8;
    private static final int MAX_PX_PER_LEAF = 28;
    private static final int MIN_LABEL_WIDTH = 120;
    private static final int MAX_LABEL_WIDTH = 360;
    private static final int DENDRO_WIDTH = 1400; // un poco más ancho
    private static final double X_GAMMA = 0.6; // <1 expande distancias pequeñas, mejora legibilidad

    public static class RenderResult {
        public final String base64;
        public final String filePath;
        public RenderResult(String base64, String filePath) { this.base64 = base64; this.filePath = filePath; }
    }

    public RenderResult renderAndSaveBase64(List<HierarchicalClusteringCore.Merge> merges, List<String> labels, String outputDir, String prefix) {
        String b64 = renderBase64(merges, labels);
        String savedPath = saveBase64PNG(b64, outputDir, prefix);
        return new RenderResult(b64, savedPath);
    }

    public byte[] renderPngBytes(List<HierarchicalClusteringCore.Merge> merges, List<String> labels) {
        String b64 = renderBase64(merges, labels);
        try { return Base64.getDecoder().decode(b64); } catch (IllegalArgumentException e) { return new byte[0]; }
    }

    public String renderBase64(List<HierarchicalClusteringCore.Merge> merges, List<String> labels) {
        int n = labels != null ? labels.size() : 0;
        if (n == 0 || merges == null || merges.isEmpty()) return emptyPng();

        double hmax = merges.stream().mapToDouble(m -> m.height).max().orElse(1.0);
        if (hmax <= 0) hmax = 1.0;

        int maxLabelLen = labels.stream().filter(Objects::nonNull).mapToInt(String::length).max().orElse(20);
        int approxCharW = 7; // px
        int labelWidth = Math.max(MIN_LABEL_WIDTH, Math.min(MAX_LABEL_WIDTH, approxCharW * Math.min(maxLabelLen, 50)));

        int pxPerLeaf = Math.max(MIN_PX_PER_LEAF, Math.min(MAX_PX_PER_LEAF, (MAX_HEIGHT - 80) / Math.max(1, n)));
        int w = Math.max(700, labelWidth + 40 + DENDRO_WIDTH);
        int h = Math.max(240, 80 + pxPerLeaf * n);
        h = Math.min(h, MAX_HEIGHT);

        BufferedImage img;
        try {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        } catch (OutOfMemoryError e) {
            w = Math.max(360, w/2);
            h = Math.max(200, h/2);
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.8f));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, Math.min(12, (int)(pxPerLeaf*0.6)))));

        int[] y = new int[2*n];
        boolean[] placed = new boolean[2*n];
        for (int i=0;i<n;i++) { y[i] = Math.min(h-60, 60 + i*pxPerLeaf); placed[i]=true; }

        int x0 = labelWidth; int xMax = w - 20;

        // Eje/escala de distancia con mapping gamma coherente
        drawDistanceAxis(g, x0, xMax, 30, hmax, X_GAMMA);

        int lastIndex = n;
        for (HierarchicalClusteringCore.Merge m : merges) {
            int li = m.left, ri = m.right;
            if (li<0 || ri<0 || li>=y.length || ri>=y.length) continue;
            int yl = y[li]; int yr = y[ri];
            int ym = (yl+yr)/2;
            double frac = m.height / hmax;
            if (Double.isNaN(frac) || Double.isInfinite(frac)) frac = 0.0;
            frac = Math.max(0.0, Math.min(1.0, frac));
            int x = x0 + (int)Math.round(Math.pow(frac, X_GAMMA) * (xMax - x0));
            // ramas
            g.drawLine(x, yl, x, yr);
            g.drawLine(x0, yl, x, yl);
            g.drawLine(x0, yr, x, yr);
            if (lastIndex < y.length) { y[lastIndex] = ym; placed[lastIndex]=true; }
            lastIndex++;
        }

        // Etiquetas (muestrar 1 de cada k si hay demasiadas)
        g.setColor(Color.DARK_GRAY);
        int step = (n > 160) ? 3 : 1;
        for (int i=0;i<n;i+=step) {
            String lab = labels.get(i) == null ? ("doc-"+i) : labels.get(i);
            if (lab.length()>40) lab = lab.substring(0,37) + "...";
            g.drawString(lab, 5, Math.min(h-5, y[i] + 4));
        }

        // Leyenda
        g.setColor(new Color(60,60,60));
        g.drawString("Altura = distancia (euclidiana sobre TF-IDF normalizado) — eje con escala no lineal (γ="+X_GAMMA+")", x0, 50);

        g.dispose();
        return toBase64(img);
    }

    private void drawDistanceAxis(Graphics2D g, int x0, int xMax, int y, double hmax, double gamma) {
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.3f));
        g.drawLine(x0, y, xMax, y);
        int ticks = 6; // 0..6
        for (int i=0;i<=ticks;i++) {
            double frac = i / (double)ticks;
            double val = hmax * frac;
            int x = x0 + (int)Math.round(Math.pow(frac, gamma) * (xMax - x0));
            g.drawLine(x, y-4, x, y+4);
            String lbl = (i==0) ? "0" : String.format(Locale.ROOT, "%.2f", val);
            g.drawString(lbl, Math.max(x-14, x0), y-6);
        }
    }

    private String saveBase64PNG(String b64, String outputDir, String prefix) {
        if (b64 == null || b64.isBlank()) return null;
        try {
            Path dir = Path.of(outputDir);
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safe = prefix.replaceAll("[^a-zA-Z0-9_-]", "_");
            Path out = dir.resolve(safe + "_" + ts + ".png");
            byte[] bytes = Base64.getDecoder().decode(b64);
            Files.write(out, bytes);
            return out.toString();
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private String emptyPng() {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,320,180);
        g.setColor(Color.GRAY); g.drawString("Sin datos", 120, 90);
        g.dispose();
        return toBase64(img);
    }

    private String toBase64(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
