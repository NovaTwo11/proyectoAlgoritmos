package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class CitationGraphRendererService {

    private final CitationGraphService graph;

    @Value("${automation.graphs.output-dir:src/main/resources/data/grafos}")
    private String outputDir;

    public static class RenderOut {
        public final String base64;
        public final String filePath;
        public RenderOut(String b64, String path) { this.base64=b64; this.filePath=path; }
    }

    public RenderOut renderGraph(Integer maxEdgesOpt) {
        // Limpiar carpeta de salida antes de renderizar
        try { cleanDir(outputDir); } catch (Exception ignore) {}

        Map<String, ArticleDTO> nodes = graph.getNodes();
        Map<String, List<CitationGraphService.Edge>> adj = graph.getAdjacency();
        int n = nodes.size();
        if (n == 0) return new RenderOut(emptyPng(), null);

        int width = Math.max(900, Math.min(3000, 300 + n * 18));
        int height = Math.max(700, Math.min(3000, 250 + n * 14));
        int padLeft = 220; // espacio para etiquetas a la izquierda si rotan
        int padRight = 40; int padTop = 60; int padBottom = 80;
        int W = width, H = height;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,W,H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.2f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // layout en círculo
        List<String> ids = new ArrayList<>(nodes.keySet());
        Collections.sort(ids); // orden estable
        double cx = padLeft + (W - padLeft - padRight) / 2.0;
        double cy = padTop + (H - padTop - padBottom) / 2.0;
        double R = Math.min((W - padLeft - padRight), (H - padTop - padBottom)) * 0.42;
        Map<String, Point> pos = new HashMap<>();
        for (int i=0;i<ids.size();i++) {
            double ang = 2*Math.PI * i / ids.size() - Math.PI/2; // inicia arriba
            int x = (int)Math.round(cx + R * Math.cos(ang));
            int y = (int)Math.round(cy + R * Math.sin(ang));
            pos.put(ids.get(i), new Point(x,y));
        }

        // recolectar y recortar aristas si es necesario
        List<CitationGraphService.Edge> edges = new ArrayList<>();
        for (List<CitationGraphService.Edge> lst : adj.values()) edges.addAll(lst);
        edges.sort((a,b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        int maxEdges = maxEdgesOpt != null ? Math.max(1, maxEdgesOpt) : 2000;
        if (edges.size() > maxEdges) edges = edges.subList(0, maxEdges);

        // dibujar aristas (dirigidas)
        for (CitationGraphService.Edge e : edges) {
            Point p1 = pos.get(e.getFrom());
            Point p2 = pos.get(e.getTo());
            if (p1==null || p2==null) continue;
            float wStroke = (float)(0.8 + 2.2*e.getSimilarity());
            g.setStroke(new BasicStroke(wStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, null, 0f));
            g.setColor(new Color(60, 120, 200, 120));
            drawArrow(g, p1.x, p1.y, p2.x, p2.y, 10, 8);
        }

        // dibujar nodos
        int r = 8;
        g.setColor(new Color(40,40,40));
        for (String id : ids) {
            Point p = pos.get(id);
            g.fillOval(p.x-r, p.y-r, 2*r, 2*r);
        }
        // etiquetas (alrededor, con leve desplazamiento)
        g.setColor(new Color(15,15,15));
        for (int i=0;i<ids.size();i++) {
            String id = ids.get(i);
            ArticleDTO a = nodes.get(id);
            String label = a!=null && a.getTitle()!=null? a.getTitle() : id;
            if (label.length()>36) label = label.substring(0,33) + "...";
            Point p = pos.get(id);
            g.drawString(label, p.x+10, p.y-10);
        }

        // Sin leyenda/descripcion superior izquierda (se omite a propósito)

        g.dispose();
        String b64 = toBase64(img);
        String path = saveFixed(b64, outputDir, "grafo dirijido");
        return new RenderOut(b64, path);
    }

    private void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2, int arrowW, int arrowH) {
        g.drawLine(x1, y1, x2, y2);
        double dx = x2 - x1, dy = y2 - y1;
        double ang = Math.atan2(dy, dx);
        AffineTransform at = g.getTransform();
        g.translate(x2, y2);
        g.rotate(ang);
        int[] xs = {0, -arrowW, -arrowW};
        int[] ys = {0, -arrowH/2, arrowH/2};
        g.fillPolygon(xs, ys, 3);
        g.setTransform(at);
    }

    private void cleanDir(String dir) {
        try {
            Path p = Path.of(dir);
            if (!Files.exists(p)) { Files.createDirectories(p); return; }
            try (var walk = Files.walk(p)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(pp -> !pp.equals(p))
                        .forEach(pp -> { try { Files.deleteIfExists(pp); } catch (Exception ignore) {} });
            }
        } catch (Exception ignore) { }
    }

    private String saveFixed(String b64, String dir, String nameNoExt) {
        try {
            Path p = Path.of(dir);
            Files.createDirectories(p);
            Path out = p.resolve(nameNoExt + ".png");
            Files.write(out, Base64.getDecoder().decode(b64));
            return out.toString();
        } catch (Exception e) { return null; }
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

    private String emptyPng() {
        try {
            BufferedImage img = new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0,0,640,360);
            g.setColor(Color.GRAY); g.drawString("Grafo vacío", 280, 180);
            g.dispose();
            return toBase64(img);
        } catch (Exception e) { return ""; }
    }
}
