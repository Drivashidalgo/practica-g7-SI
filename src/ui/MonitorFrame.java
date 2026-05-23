package ui;

import models.CicloFraude;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Ventana principal del monitor antifraude.
 * Todos los métodos que mutan UI asumen que se llaman desde el EDT.
 */
public class MonitorFrame extends JFrame {

    private final IndicadorEstado indicador = new IndicadorEstado();
    private final JLabel estadoTexto = new JLabel();
    private final JLabel contador = new JLabel("0");
    private final JLabel contadorEtiqueta = new JLabel("ciclos detectados");
    private final JPanel feed = new JPanel();
    private final JScrollPane scroll;
    private final JLabel barraDF = new JLabel("DF: interfaz-visualizacion");
    private final JLabel barraConn = new JLabel("a la espera del Analista");

    public MonitorFrame() {
        super("Sistema de detección de blanqueo de capitales");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Paleta.FONDO);
        setLayout(new BorderLayout());

        add(construirCabecera(), BorderLayout.NORTH);

        feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
        feed.setBackground(Paleta.FONDO);
        feed.setBorder(new EmptyBorder(8, 14, 8, 14));

        // Wrapper para que el feed quede anclado arriba dentro del JScrollPane
        JPanel feedWrap = new JPanel(new BorderLayout());
        feedWrap.setBackground(Paleta.FONDO);
        feedWrap.add(feed, BorderLayout.NORTH);

        scroll = new JScrollPane(
                feedWrap,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getViewport().setBackground(Paleta.FONDO);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        add(construirBarraEstado(), BorderLayout.SOUTH);

        setEstadoEsperando();
    }

    private JPanel construirCabecera() {
        JPanel cab = new JPanel(new BorderLayout());
        cab.setBackground(Paleta.FONDO_CABECERA);
        cab.setBorder(new EmptyBorder(14, 18, 14, 18));

        // Izquierda: título + indicador + estado
        JPanel izq = new JPanel();
        izq.setOpaque(false);
        izq.setLayout(new BoxLayout(izq, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel("Sistema de detección de blanqueo de capitales");
        titulo.setForeground(Paleta.TEXTO_PRINCIPAL);
        titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        JPanel filaEstado = new JPanel();
        filaEstado.setOpaque(false);
        filaEstado.setLayout(new BoxLayout(filaEstado, BoxLayout.X_AXIS));
        indicador.setAlignmentY(0.5f);
        estadoTexto.setForeground(Paleta.TEXTO_SECUNDARIO);
        estadoTexto.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        filaEstado.add(indicador);
        filaEstado.add(Box.createHorizontalStrut(8));
        filaEstado.add(estadoTexto);

        izq.add(titulo);
        izq.add(Box.createVerticalStrut(6));
        izq.add(filaEstado);

        // Derecha: contador grande
        JPanel der = new JPanel();
        der.setOpaque(false);
        der.setLayout(new BoxLayout(der, BoxLayout.Y_AXIS));

        contador.setForeground(Paleta.BORDE_ALERTA);
        contador.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        contador.setAlignmentX(JPanel.RIGHT_ALIGNMENT);
        contador.setHorizontalAlignment(SwingConstants.RIGHT);

        contadorEtiqueta.setForeground(Paleta.TEXTO_SECUNDARIO);
        contadorEtiqueta.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        contadorEtiqueta.setAlignmentX(JPanel.RIGHT_ALIGNMENT);
        contadorEtiqueta.setHorizontalAlignment(SwingConstants.RIGHT);

        der.add(contador);
        der.add(contadorEtiqueta);

        cab.add(izq, BorderLayout.WEST);
        cab.add(der, BorderLayout.EAST);
        return cab;
    }

    private JPanel construirBarraEstado() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(Paleta.FONDO_CABECERA);
        barra.setBorder(new EmptyBorder(6, 14, 6, 14));

        barraDF.setForeground(Paleta.TEXTO_SECUNDARIO);
        barraDF.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        barraConn.setForeground(Paleta.TEXTO_SECUNDARIO);
        barraConn.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        barraConn.setHorizontalAlignment(SwingConstants.RIGHT);

        barra.add(barraDF, BorderLayout.WEST);
        barra.add(barraConn, BorderLayout.EAST);
        return barra;
    }

    public void setEstadoEsperando() {
        indicador.setColor(Paleta.ACENTO_ESPERA);
        estadoTexto.setText("Esperando datos");
        barraConn.setText("a la espera del Analista");
    }

    public void setEstadoMonitorizando() {
        indicador.setColor(Paleta.ACENTO_ACTIVO);
        estadoTexto.setText("Monitorización activa");
        barraConn.setText("conectado al Analista");
    }

    public void setEstadoFinalizado() {
        indicador.setColor(Paleta.ACENTO_FIN);
        estadoTexto.setText("Monitorización finalizada");
        barraConn.setText("stream finalizado");
    }

    public void addAlerta(CicloFraude ciclo) {
        AlertaCard card = new AlertaCard(ciclo);
        feed.add(card, 0);
        feed.add(Box.createVerticalStrut(8), 1);
        contador.setText(Integer.toString(ciclo.getNumero()));
        feed.revalidate();
        feed.repaint();
        scroll.getVerticalScrollBar().setValue(0);
    }

    /** Círculo de color usado en la cabecera. */
    private static class IndicadorEstado extends JPanel {
        private Color color = Paleta.ACENTO_ESPERA;
        IndicadorEstado() {
            setOpaque(false);
            setPreferredSize(new Dimension(20, 20));
            setMaximumSize(new Dimension(20, 20));
        }
        void setColor(Color c) { this.color = c; repaint(); }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.dispose();
        }
    }
}
