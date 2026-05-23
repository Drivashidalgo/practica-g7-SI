package ui;

import models.CicloFraude;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Mini-grafo del ciclo: nodos en circunferencia, aristas dirigidas con flecha
 * e importe junto a cada arista. Nodo de origen destacado en rojo.
 */
public class GrafoCicloPanel extends JPanel {

    private static final int W = 155;
    private static final int H = 140;
    private static final int RADIO_NODO = 15;
    private static final int CABEZA_FLECHA = 8;

    private final CicloFraude ciclo;

    public GrafoCicloPanel(CicloFraude ciclo) {
        this.ciclo = ciclo;
        setPreferredSize(new Dimension(W, H));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;
            int r = Math.min(w, h) / 2 - RADIO_NODO - 6;

            List<String> cuentas = ciclo.getCuentas();
            List<Double> importes = ciclo.getImportes();
            int n = cuentas.size();

            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                double ang = 2 * Math.PI * i / n - Math.PI / 2;
                xs[i] = cx + r * Math.cos(ang);
                ys[i] = cy + r * Math.sin(ang);
            }

            // Aristas (debajo de los nodos)
            g2.setStroke(new BasicStroke(1.6f));
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                dibujarFlecha(g2, xs[i], ys[i], xs[j], ys[j]);
                dibujarImporte(g2, xs[i], ys[i], xs[j], ys[j], importes.get(i));
            }

            // Nodos encima
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            for (int i = 0; i < n; i++) {
                Color fill = (i == 0) ? Paleta.NODO_ORIGEN : Paleta.NODO_NORMAL;
                int nx = (int) Math.round(xs[i]) - RADIO_NODO;
                int ny = (int) Math.round(ys[i]) - RADIO_NODO;
                g2.setColor(fill);
                g2.fillOval(nx, ny, RADIO_NODO * 2, RADIO_NODO * 2);
                g2.setColor(Paleta.FONDO);
                g2.drawOval(nx, ny, RADIO_NODO * 2, RADIO_NODO * 2);

                String etiqueta = sufijo(cuentas.get(i));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(etiqueta);
                int tx = (int) Math.round(xs[i]) - tw / 2;
                int ty = (int) Math.round(ys[i]) + fm.getAscent() / 2 - 1;
                g2.setColor(Color.WHITE);
                g2.drawString(etiqueta, tx, ty);
            }
        } finally {
            g2.dispose();
        }
    }

    private void dibujarFlecha(Graphics2D g2, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double ux = dx / len;
        double uy = dy / len;

        // Línea borde-borde de los círculos
        double sx = x1 + ux * RADIO_NODO;
        double sy = y1 + uy * RADIO_NODO;
        double ex = x2 - ux * RADIO_NODO;
        double ey = y2 - uy * RADIO_NODO;

        g2.setColor(Paleta.ARISTA);
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy),
                    (int) Math.round(ex), (int) Math.round(ey));

        // Cabeza de flecha en (ex, ey)
        double ang = Math.atan2(dy, dx);
        Path2D.Double cabeza = new Path2D.Double();
        cabeza.moveTo(0, 0);
        cabeza.lineTo(-CABEZA_FLECHA, -CABEZA_FLECHA / 2.0);
        cabeza.lineTo(-CABEZA_FLECHA,  CABEZA_FLECHA / 2.0);
        cabeza.closePath();
        AffineTransform at = AffineTransform.getTranslateInstance(ex, ey);
        at.rotate(ang);
        g2.fill(at.createTransformedShape(cabeza));
    }

    private void dibujarImporte(Graphics2D g2, double x1, double y1, double x2, double y2, double importe) {
        double mx = (x1 + x2) / 2.0;
        double my = (y1 + y2) / 2.0;
        // Desplazamiento perpendicular hacia "fuera" del centro del panel
        double cxp = getWidth() / 2.0;
        double cyp = getHeight() / 2.0;
        double ox = mx - cxp;
        double oy = my - cyp;
        double on = Math.hypot(ox, oy);
        if (on > 1e-6) {
            ox = ox / on * 10;
            oy = oy / on * 10;
        }
        String txt = formatoCorto(importe);
        g2.setColor(Paleta.IMPORTE);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(txt);
        int tx = (int) Math.round(mx + ox) - tw / 2;
        int ty = (int) Math.round(my + oy) + fm.getAscent() / 2 - 1;
        g2.drawString(txt, tx, ty);
    }

    private static String sufijo(String cuenta) {
        if (cuenta == null) return "?";
        if (cuenta.length() <= 3) return cuenta;
        return cuenta.substring(cuenta.length() - 3);
    }

    private static String formatoCorto(double v) {
        if (v >= 1000) {
            double k = v / 1000.0;
            if (k == Math.floor(k)) return ((long) k) + "k";
            return String.format("%.1fk", k);
        }
        if (v == Math.floor(v)) return Long.toString((long) v);
        return String.format("%.0f", v);
    }
}
