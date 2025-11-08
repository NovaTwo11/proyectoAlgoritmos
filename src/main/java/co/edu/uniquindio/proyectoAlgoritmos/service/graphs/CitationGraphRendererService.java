package co.edu.uniquindio.proyectoAlgoritmos.service.graphs;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CitationGraphRendererService {

    private final CitationGraphService graph;

    @Value("${automation.graphs.output-dir:src/main/resources/data/grafos}")
    private String outputDir;

    public static class RenderOut {
        public final String base64;   // sin prefijo
        public final String filePath;
        public RenderOut(String b64, String path) { this.base64=b64; this.filePath=path; }
        public String dataUri() { return base64==null? null : "data:image/png;base64," + base64; }
    }

    /** Genera PNG con layout en rejilla centrada (sin usar peso/sim para posición) y devuelve base64 + filePath. */
    public RenderOut renderGraph(Integer maxEdgesOpt) {
        try { cleanDir(outputDir); } catch (Exception ignore) {}

        Map<String, ArticleDTO> nodes = graph.getNodes();
        Map<String, List<CitationGraphService.Edge>> adj = graph.getAdjacency();
        int n = nodes.size();
        if (n == 0) return new RenderOut(emptyPng(), null);

        // Canvas (márgenes mínimos)
        int W = Math.max(1100, Math.min(3600, 300 + n * 18));
        int H = Math.max(800,  Math.min(3000, 260 + n * 16));
        int pad = 36;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,W,H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Datos base
        List<String> ids = new ArrayList<>(nodes.keySet());
        Collections.sort(ids);

        // Grados + aristas (para tamaños/estética; NO se usan en layout)
        Map<String, Integer> degree = new HashMap<>();
        for (String id : ids) degree.put(id, 0);
        List<CitationGraphService.Edge> edges = adj.values().stream().flatMap(List::stream).collect(Collectors.toList());
        for (var e : edges) { degree.compute(e.getFrom(), (k,v)->(v==null?0:v)+1); degree.compute(e.getTo(), (k,v)->(v==null?0:v)+1); }

        // Si hay demasiadas aristas para dibujar, recortamos a las más “fuertes” solo por visibilidad (pero el layout no depende de esto)
        edges.sort((a,b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        int maxEdges = maxEdgesOpt != null ? Math.max(1, maxEdgesOpt) : 3000;
        if (edges.size() > maxEdges) edges = edges.subList(0, maxEdges);

        // SCC -> color
        var sccs = graph.components().getStrong();
        Map<String,Integer> sccIdx = new HashMap<>();
        for (int i=0;i<sccs.size();i++) for (String v : sccs.get(i)) sccIdx.put(v, i);
        Color[] palette = palette();

        // Área circular de trabajo (para aspecto orgánico y evitar “cuadrado”)
        float cx = W / 2f, cy = H / 2f;
        float Rbound = (float)(Math.min(W, H) * 0.48) - pad;

        // === POSICIONAMIENTO DE NODOS: REJILLA CENTRADA SIN USAR PESOS/SIM ===
        Map<String, Point.Float> pos = placeNodesGridCentered(ids, n, cx, cy, Rbound, W, H);

        // === DIBUJO DE ARISTAS (curvas), ignorando peso para posición ===
        for (var e : edges) {
            Point.Float p1 = pos.get(e.getFrom());
            Point.Float p2 = pos.get(e.getTo());
            if (p1==null || p2==null) continue;
            float sim = (float)Math.max(0, Math.min(1, e.getSimilarity()));
            float wStroke = 0.6f + 2.2f*sim;
            int alpha = (int)Math.round(40 + 170*sim);
            g.setStroke(new BasicStroke(wStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(60, 120, 200, Math.max(30, Math.min(210, alpha))));
            drawCurvedArrow(g, p1.x, p1.y, p2.x, p2.y, 12, 9, 0.22f);
        }

        // === NODOS ===
        for (String id : ids) {
            Point.Float p = pos.get(id);
            int deg = degree.getOrDefault(id, 0);
            int r = 5 + Math.min(11, (int)Math.round(Math.log1p(deg+1))); // 5..16
            int ci = sccIdx.getOrDefault(id, -1);
            Color c = ci>=0? palette[ci % palette.length] : new Color(40,40,40);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 220));
            g.fillOval(Math.round(p.x)-r, Math.round(p.y)-r, 2*r, 2*r);
            g.setColor(new Color(20,20,20,200));
            g.setStroke(new BasicStroke(1.1f));
            g.drawOval(Math.round(p.x)-r, Math.round(p.y)-r, 2*r, 2*r);
        }

        // === Etiquetas (títulos) con fondo translucido y clamp ===
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int nLabels = ids.size();
        int maxLabels = nLabels <= 160 ? nLabels : (int)Math.round(nLabels * 0.55); // ~55% si hay demasiados
        double labelProb = nLabels <= 160 ? 1.0 : (maxLabels / (double)nLabels);
        Random pick = new Random(20211);
        for (String id : ids) {
            if (pick.nextDouble() > labelProb) continue;
            ArticleDTO a = nodes.get(id);
            String label = a!=null && a.getTitle()!=null? a.getTitle() : id;
            if (label.length() > 64) label = label.substring(0, 61) + "...";

            Point.Float p = pos.get(id);
            int tx = Math.round(p.x) + 10, ty = Math.round(p.y) - 8;

            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(label);
            int th = fm.getAscent();

            // Clamp para no salirse del lienzo
            int x0 = Math.max(2, Math.min(tx, W - tw - 2));
            int y0 = Math.max(th + 2, Math.min(ty, H - 2));

            // Fondo para legibilidad
            g.setColor(new Color(255,255,255,200));
            g.fillRoundRect(x0-3, y0-th, Math.min(900, tw+6), th+4, 6, 6);

            // Texto
            g.setColor(new Color(25,25,25));
            g.drawString(label, x0, y0);
        }

        g.dispose();

        String b64 = toBase64(img);
        String path = saveTimestamped(b64, outputDir, "grafo_citaciones_grid_centered");
        return new RenderOut(b64, path);
    }

    // ================== POSICIONAMIENTO: GRID CENTRADO QUE LLENA EL CENTRO ==================
    /**
     * Ubica los nodos en una rejilla centrada dentro de un disco de radio Rbound.
     * - NO usa pesos ni similitudes.
     * - Ordena celdas por distancia al centro para ocupar el centro primero.
     * - Aplica jitter aleatorio por celda y proyecta cualquier punto fuera del disco hacia adentro.
     */
    private Map<String, Point.Float> placeNodesGridCentered(
            List<String> ids, int n, float cx, float cy, float Rbound, int W, int H
    ) {
        Map<String, Point.Float> pos = new LinkedHashMap<>();

        if (n == 1) {
            pos.put(ids.get(0), new Point.Float(cx, cy));
            return pos;
        }

        // Dimensiones de la rejilla: respetar relación de aspecto del lienzo
        // cols ≈ sqrt(n * W/H), rows = ceil(n/cols)
        int cols = (int)Math.ceil(Math.sqrt(n * (W / (double)H)));
        cols = Math.max(2, cols);
        int rows = (int)Math.ceil(n / (double)cols);

        // Caja cuadrada dentro del círculo (ligeramente menor al diámetro)
        double side = Math.min(2*Rbound, Math.min(W, H) - 2*36.0) * 0.95; // 95% del diámetro
        if (side <= 0) side = Math.min(W, H) * 0.8;
        double cellW = side / cols;
        double cellH = side / rows;
        double originX = cx - side/2.0;
        double originY = cy - side/2.0;

        // Construir lista de celdas con su distancia al centro (centro de celda)
        class Cell { int r,c; double d2; double cx, cy; }
        List<Cell> cells = new ArrayList<>();
        double gcx = cols / 2.0, gcy = rows / 2.0;
        for (int r=0; r<rows; r++) {
            for (int c=0; c<cols; c++) {
                double ccx = originX + (c + 0.5) * cellW;
                double ccy = originY + (r + 0.5) * cellH;
                // Distancia al centro de la imagen (no al centro del grid) para llenar el centro visual
                double dx = ccx - cx;
                double dy = ccy - cy;
                Cell cell = new Cell();
                cell.r = r; cell.c = c; cell.cx = ccx; cell.cy = ccy;
                cell.d2 = dx*dx + dy*dy;
                cells.add(cell);
            }
        }
        // Ordenar por cercanía al centro (para ocupar primero el centro)
        cells.sort(Comparator.comparingDouble(a -> a.d2));

        // Asignar nodos a celdas (hasta n)
        Random rnd = new Random(1234567);
        double jitter = 0.35 * Math.min(cellW, cellH); // dispersión dentro de la celda
        int assigned = 0;
        for (String id : ids) {
            if (assigned >= cells.size()) break;
            Cell cell = cells.get(assigned++);
            float x = (float)(cell.cx + (rnd.nextDouble()*2 - 1) * jitter);
            float y = (float)(cell.cy + (rnd.nextDouble()*2 - 1) * jitter);

            // Proyección al disco si quedó fuera (con margen por radio de nodo/flecha)
            double margin = 12.0;
            double maxR = Math.max(1.0, Rbound - margin);
            double rx = x - cx, ry = y - cy;
            double r = Math.hypot(rx, ry);
            if (r > maxR) {
                double ux = rx / r, uy = ry / r;
                x = (float)(cx + ux * maxR);
                y = (float)(cy + uy * maxR);
            }
            pos.put(id, new Point.Float(x, y));
        }

        // Si hubiese más nodos que celdas (raro), caen en una “corona” circular
        while (assigned < ids.size()) {
            String id = ids.get(assigned++);
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double rad = Math.sqrt(rnd.nextDouble()) * (Rbound - 14.0);
            float x = (float)(cx + rad * Math.cos(ang));
            float y = (float)(cy + rad * Math.sin(ang));
            pos.put(id, new Point.Float(x, y));
        }

        return pos;
    }

    // ================== DIBUJO ==================
    private void drawCurvedArrow(Graphics2D g, float x1, float y1, float x2, float y2, int aw, int ah, float bend) {
        float mx = (x1 + x2) * 0.5f;
        float my = (y1 + y2) * 0.5f;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float)Math.max(1e-3, Math.hypot(dx, dy));
        float nx = -dy / len, ny = dx / len; // normal
        float bx = mx + nx * bend * len;
        float by = my + ny * bend * len;

        QuadCurve2D.Float q = new QuadCurve2D.Float(x1, y1, bx, by, x2, y2);
        g.draw(q);

        float tx = x2 - bx, ty = y2 - by;
        double ang = Math.atan2(ty, tx);
        AffineTransform at = g.getTransform();
        g.translate(x2, y2);
        g.rotate(ang);
        int[] xs = {0, -aw, -aw};
        int[] ys = {0, -ah/2, ah/2};
        g.fillPolygon(xs, ys, 3);
        g.setTransform(at);
    }

    // ================== UTILIDADES ==================
    private Color[] palette() {
        return new Color[] {
                new Color(61,148,255), new Color(255,99,132), new Color(75,192,192),
                new Color(255,205,86), new Color(153,102,255), new Color(255,159,64),
                new Color(52,199,89),  new Color(90,200,250), new Color(255,112,67),
                new Color(169,169,169),new Color(100,181,246),new Color(244,143,177)
        };
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
        } catch (Exception ignore) {}
    }

    private String saveTimestamped(String b64, String dir, String prefix) {
        try {
            Path p = Path.of(dir);
            Files.createDirectories(p);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safe = prefix.replaceAll("[^a-zA-Z0-9_-]", "_");
            Path out = p.resolve(safe + "_" + ts + ".png");
            Files.write(out, Base64.getDecoder().decode(b64));
            return out.toString();
        } catch (Exception e) { return null; }
    }

    private String toBase64(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return ""; }
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
