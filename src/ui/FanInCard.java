package ui;

import models.AlertaFanIn;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;

/**
 * Tarjeta visual de una alerta de cuenta sospechosa de fan-in.
 * Borde izquierdo coloreado por severidad (rojo / naranja / ámbar),
 * score numérico + barra horizontal de probabilidad.
 */
public class FanInCard extends JPanel {

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public FanInCard(AlertaFanIn alerta) {
        Color colorSev = colorPorSeveridad(alerta.getSeveridad());

        setLayout(new BorderLayout(10, 0));
        setBackground(Paleta.CARD_BG);
        setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, colorSev),
                new EmptyBorder(10, 12, 10, 12)
        ));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        add(construirInfo(alerta, colorSev), BorderLayout.CENTER);

        // Altura preferida fija para que el feed quede uniforme
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
    }

    private JPanel construirInfo(AlertaFanIn alerta, Color colorSev) {
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        // Cabecera: "◎  Cuenta #4481                HH:mm:ss"
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);

        JLabel titulo = new JLabel("◎  Cuenta #" + alerta.getCuenta());
        titulo.setForeground(Paleta.TEXTO_PRINCIPAL);
        titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        JLabel hora = new JLabel(alerta.getHora().format(HORA_FMT));
        hora.setForeground(Paleta.TEXTO_SECUNDARIO);
        hora.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        hora.setHorizontalAlignment(SwingConstants.RIGHT);

        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(hora, BorderLayout.EAST);
        cabecera.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Fila: score textual | barra de probabilidad | etiqueta
        JPanel fila = new JPanel();
        fila.setOpaque(false);
        fila.setLayout(new BoxLayout(fila, BoxLayout.X_AXIS));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel scoreLbl = new JLabel(String.format("%.4f", alerta.getScore()));
        scoreLbl.setForeground(colorSev);
        scoreLbl.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));

        BarraScore barra = new BarraScore(alerta.getScore(), colorSev);

        JLabel etiqueta = new JLabel("patrón fan-in");
        etiqueta.setForeground(Paleta.TEXTO_SECUNDARIO);
        etiqueta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        fila.add(scoreLbl);
        fila.add(Box.createHorizontalStrut(14));
        fila.add(barra);
        fila.add(Box.createHorizontalStrut(14));
        fila.add(etiqueta);
        fila.add(Box.createHorizontalGlue());

        info.add(cabecera);
        info.add(Box.createVerticalStrut(6));
        info.add(fila);
        return info;
    }

    private static Color colorPorSeveridad(AlertaFanIn.Severidad sev) {
        switch (sev) {
            case ALTA:  return Paleta.BORDE_ALERTA;   // rojo
            case MEDIA: return Paleta.IMPORTE;        // naranja
            case BAJA:  return Paleta.ACENTO_ESPERA;  // ámbar
            default:    return Paleta.TEXTO_SECUNDARIO;
        }
    }

    /** Barra horizontal de probabilidad. Mapea score [0.7, 1.0] a [0%, 100%]. */
    private static class BarraScore extends JPanel {
        private static final double MIN_SCORE = 0.7;
        private static final double MAX_SCORE = 1.0;

        private final double score;
        private final Color color;

        BarraScore(double score, Color color) {
            this.score = score;
            this.color = color;
            setOpaque(false);
            Dimension d = new Dimension(160, 18);
            setPreferredSize(d);
            setMinimumSize(new Dimension(80, 18));
            setMaximumSize(new Dimension(240, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int barH = 8;
                int y = (h - barH) / 2;

                // Fondo
                g2.setColor(Paleta.FONDO);
                g2.fillRoundRect(0, y, w, barH, barH, barH);

                // Relleno proporcional al score (clamped)
                double pct = (score - MIN_SCORE) / (MAX_SCORE - MIN_SCORE);
                if (pct < 0) pct = 0;
                if (pct > 1) pct = 1;
                int relleno = (int) Math.round(w * pct);
                if (relleno > 0) {
                    g2.setColor(color);
                    g2.fillRoundRect(0, y, relleno, barH, barH, barH);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
