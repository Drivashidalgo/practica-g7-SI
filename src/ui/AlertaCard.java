package ui;

import models.CicloFraude;

import javax.swing.BorderFactory;
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
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tarjeta visual de una alerta de ciclo de fraude.
 * Borde izquierdo rojo + fondo oscuro + mini-grafo a la derecha.
 */
public class AlertaCard extends JPanel {

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final NumberFormat IMPORTE_FMT = NumberFormat.getNumberInstance(new Locale("es", "ES"));

    public AlertaCard(CicloFraude ciclo) {
        setLayout(new BorderLayout(10, 0));
        setBackground(Paleta.CARD_BG);
        setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, Paleta.BORDE_ALERTA),
                new EmptyBorder(10, 12, 10, 12)
        ));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        add(construirInfo(ciclo), BorderLayout.CENTER);
        add(envolverGrafo(new GrafoCicloPanel(ciclo)), BorderLayout.EAST);

        // Altura preferida fija para que el feed quede uniforme
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
    }

    private JPanel construirInfo(CicloFraude ciclo) {
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        // Cabecera: "⚠  Ciclo #N                HH:mm:ss"
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);

        JLabel titulo = new JLabel("⚠  Ciclo #" + ciclo.getNumero());
        titulo.setForeground(Paleta.TEXTO_PRINCIPAL);
        titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        JLabel hora = new JLabel(ciclo.getHora().format(HORA_FMT));
        hora.setForeground(Paleta.TEXTO_SECUNDARIO);
        hora.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        hora.setHorizontalAlignment(SwingConstants.RIGHT);

        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(hora, BorderLayout.EAST);
        cabecera.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Secuencia: C429 → C871 → C512 → C429
        JLabel secuencia = new JLabel(secuenciaTexto(ciclo));
        secuencia.setForeground(Paleta.TEXTO_PRINCIPAL);
        secuencia.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        secuencia.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Importe total en naranja
        JLabel total = new JLabel(IMPORTE_FMT.format(ciclo.getTotal()) + " € en circulación");
        total.setForeground(Paleta.IMPORTE);
        total.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        total.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(cabecera);
        info.add(Box.createVerticalStrut(4));
        info.add(secuencia);
        info.add(Box.createVerticalStrut(2));
        info.add(total);
        return info;
    }

    private JPanel envolverGrafo(GrafoCicloPanel grafo) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        wrap.add(grafo, BorderLayout.CENTER);
        return wrap;
    }

    private static String secuenciaTexto(CicloFraude ciclo) {
        StringBuilder sb = new StringBuilder();
        for (String c : ciclo.getCuentas()) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(c);
        }
        // Cierre del ciclo (vuelve al primero)
        sb.append(" → ").append(ciclo.getCuentas().get(0));
        return sb.toString();
    }
}
