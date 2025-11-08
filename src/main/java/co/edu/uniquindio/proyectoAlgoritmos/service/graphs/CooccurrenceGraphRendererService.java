package co.edu.uniquindio.proyectoAlgoritmos.service.graphs;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class CooccurrenceGraphRendererService {

    private final CooccurrenceGraphService service;

    @Value("${automation.graphs.output-dir:src/main/resources/data/grafos}")
    private String outputDir;

    public static class RenderOut {
        public final String base64;
        public final String filePath;
        public RenderOut(String b64, String path) { this.base64=b64; this.filePath=path; }
    }

    public RenderOut render(Integer maxEdgesOpt) {
        String targetName = "grafo no dirijido";
        try { deleteIfExists(outputDir, targetName + ".png"); } catch (Exception ignore) {}

        Set<String> nodes = service.getVocabulary();
        Map<String, String> labels = service.getLabelsMap();
        Map<String, Map<String,Integer>> adj = service.getAdjacency();
        int n = nodes.size();
        if (n == 0) return new RenderOut(emptyPng(), null);

        int width = Math.max(800, Math.min(2400, 260 + n * 16));
        int height = Math.max(600, Math.min(2000, 220 + n * 12));
        int padLeft = 40, padRight = 40, padTop = 40, padBottom = 40;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,width,height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.1f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

        java.util.List<String> ids = new ArrayList<>(nodes);
        Collections.sort(ids);
        double cx = padLeft + (width - padLeft - padRight) / 2.0;
        double cy = padTop + (height - padTop - padBottom) / 2.0;
        double R = Math.min((width - padLeft - padRight), (height - padTop - padBottom)) * 0.42;
        Map<String, Point> pos = new HashMap<>();
        for (int i=0;i<ids.size();i++) {
            double ang = 2*Math.PI * i / ids.size() - Math.PI/2;
            int x = (int)Math.round(cx + R * Math.cos(ang));
            int y = (int)Math.round(cy + R * Math.sin(ang));
            pos.put(ids.get(i), new Point(x,y));
        }

        java.util.List<EdgeDraw> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String,Integer>> en : adj.entrySet()) {
            String u = en.getKey();
            for (Map.Entry<String,Integer> e2 : en.getValue().entrySet()) {
                String v = e2.getKey();
                if (u.compareTo(v) < 0) edges.add(new EdgeDraw(u,v,e2.getValue()));
            }
        }
        edges.sort((a,b) -> Integer.compare(b.w, a.w));
        int maxEdges = maxEdgesOpt != null ? Math.max(1, maxEdgesOpt) : 2000;
        if (edges.size() > maxEdges) edges = edges.subList(0, maxEdges);

        for (EdgeDraw e : edges) {
            Point p1 = pos.get(e.u);
            Point p2 = pos.get(e.v);
            if (p1==null || p2==null) continue;
            float wStroke = (float)(0.5 + Math.min(6.0, Math.log(1 + e.w)));
            g.setStroke(new BasicStroke(wStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(80, 160, 80, 130));
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
        }

        int r = 7;
        g.setColor(new Color(40,40,40));
        for (String id : ids) {
            Point p = pos.get(id);
            g.fillOval(p.x-r, p.y-r, 2*r, 2*r);
        }
        g.setColor(new Color(15,15,15));
        for (String id : ids) {
            Point p = pos.get(id);
            String label = labels.getOrDefault(id, id);
            if (label.length()>36) label = label.substring(0,33) + "...";
            g.drawString(label, p.x+10, p.y-8);
        }

        g.dispose();
        String b64 = toBase64(img);
        String path = saveFixed(b64, outputDir, targetName);
        return new RenderOut(b64, path);
    }

    private record EdgeDraw(String u, String v, int w) {}

    private void deleteIfExists(String dir, String fileName) {
        try {
            Path p = Path.of(dir);
            Files.createDirectories(p);
            Path target = p.resolve(fileName);
            Files.deleteIfExists(target);
        } catch (Exception ignore) { }
    }

    private String saveFixed(String b64, String dir, String nameNoExt) {
        try {
            Path p = Path.of(dir);
            Files.createDirectories(p);
            Path out = p.resolve(nameNoExt + ".png");
            Files.write(out, java.util.Base64.getDecoder().decode(b64));
            return out.toString();
        } catch (Exception e) { return null; }
    }

    private String toBase64(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
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
