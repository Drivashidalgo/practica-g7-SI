package ui;

import models.AlertaFanIn;
import models.CicloFraude;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana principal del monitor antifraude.
 * Dos pestañas:
 *   - Ciclos: alertas de AgentAnalyst (cycle detection con Tarjan).
 *   - Fan-in: alertas de AgentScoring (GNN, cuentas sospechosas).
 * Todos los métodos que mutan UI asumen que se llaman desde el EDT.
 */
public class MonitorFrame extends JFrame {

    private static final int TAB_CICLOS = 0;
    private static final int TAB_FANIN  = 1;

    private final IndicadorEstado indicador = new IndicadorEstado();
    private final JLabel estadoTexto = new JLabel();
    private final JLabel contador = new JLabel("0");
    private final JLabel contadorEtiqueta = new JLabel("ciclos detectados");

    private final JPanel feedCiclos = new JPanel();
    private final JPanel feedFanIn  = new JPanel();
    private final JScrollPane scrollCiclos;
    private final JScrollPane scrollFanIn;
    private final JTabbedPane tabs = new JTabbedPane();

    private final JLabel barraDF   = new JLabel("DF: interfaz-visualizacion");
    private final JLabel barraConn = new JLabel("a la espera del Analista");

    private int contadorCiclos = 0;
    private int contadorFanIn  = 0;

    /** Alertas fan-in ya añadidas, en orden de score descendente (refleja el feed). */
    private final List<AlertaFanIn> fanInOrdenados = new ArrayList<>();

    public MonitorFrame() {
        super("Sistema de detección de blanqueo de capitales");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Paleta.FONDO);
        setLayout(new BorderLayout());

        add(construirCabecera(), BorderLayout.NORTH);

        scrollCiclos = construirFeed(feedCiclos);
        scrollFanIn  = construirFeed(feedFanIn);
        configurarTabs();
        add(tabs, BorderLayout.CENTER);

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

        // Derecha: contador grande (cambia según pestaña activa)
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

    private JScrollPane construirFeed(JPanel feed) {
        feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
        feed.setBackground(Paleta.FONDO);
        feed.setBorder(new EmptyBorder(8, 14, 8, 14));

        // Wrapper para anclar el feed arriba dentro del JScrollPane
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Paleta.FONDO);
        wrap.add(feed, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(
                wrap,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getViewport().setBackground(Paleta.FONDO);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void configurarTabs() {
        // Estilo dark del JTabbedPane (los look&feel por defecto suelen usar
        // grises del SO; forzamos los colores de la paleta).
        UIManager.put("TabbedPane.contentAreaColor",      Paleta.FONDO);
        UIManager.put("TabbedPane.selected",              Paleta.FONDO);
        UIManager.put("TabbedPane.background",            Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.foreground",            Paleta.TEXTO_PRINCIPAL);
        UIManager.put("TabbedPane.tabAreaBackground",     Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.borderHightlightColor", Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.darkShadow",            Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.light",                 Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.shadow",                Paleta.FONDO_CABECERA);
        UIManager.put("TabbedPane.tabInsets",             new Insets(8, 16, 8, 16));

        tabs.setBackground(Paleta.FONDO_CABECERA);
        tabs.setForeground(Paleta.TEXTO_PRINCIPAL);
        tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        tabs.setFocusable(false);

        tabs.addTab("Ciclos (0)", scrollCiclos);
        tabs.addTab("Fan-in (0)", scrollFanIn);
        tabs.setBackgroundAt(TAB_CICLOS, Paleta.FONDO_CABECERA);
        tabs.setBackgroundAt(TAB_FANIN,  Paleta.FONDO_CABECERA);

        tabs.addChangeListener(e -> sincronizarHeader());
    }

    private void sincronizarHeader() {
        if (tabs.getSelectedIndex() == TAB_FANIN) {
            contador.setText(Integer.toString(contadorFanIn));
            contadorEtiqueta.setText("cuentas sospechosas");
        } else {
            contador.setText(Integer.toString(contadorCiclos));
            contadorEtiqueta.setText("ciclos detectados");
        }
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

    /** Añade una alerta de ciclo. Llamar desde el EDT. */
    public void addAlerta(CicloFraude ciclo) {
        AlertaCard card = new AlertaCard(ciclo);
        feedCiclos.add(card, 0);
        feedCiclos.add(Box.createVerticalStrut(8), 1);
        contadorCiclos = ciclo.getNumero();
        tabs.setTitleAt(TAB_CICLOS, "Ciclos (" + contadorCiclos + ")");
        if (tabs.getSelectedIndex() == TAB_CICLOS) {
            contador.setText(Integer.toString(contadorCiclos));
        }
        feedCiclos.revalidate();
        feedCiclos.repaint();
        scrollCiclos.getVerticalScrollBar().setValue(0);
    }

    /**
     * Añade una alerta de fan-in en la posición correcta para mantener el feed
     * ordenado por score descendente. En empate de score, la nueva queda
     * detrás de las anteriores (orden estable de llegada).
     * Llamar desde el EDT.
     */
    public void addFanIn(AlertaFanIn alerta) {
        // 1. Buscar índice de inserción para mantener orden descendente por score.
        int idx = 0;
        while (idx < fanInOrdenados.size()
                && fanInOrdenados.get(idx).getScore() >= alerta.getScore()) {
            idx++;
        }
        fanInOrdenados.add(idx, alerta);

        // 2. En el feed la disposición es [card0, strut0, card1, strut1, ...],
        //    así que el índice de componente es idx * 2 para la tarjeta.
        FanInCard card = new FanInCard(alerta);
        int compIdx = idx * 2;
        feedFanIn.add(card, compIdx);
        feedFanIn.add(Box.createVerticalStrut(8), compIdx + 1);

        // 3. Contador y título de la pestaña.
        contadorFanIn++;
        tabs.setTitleAt(TAB_FANIN, "Fan-in (" + contadorFanIn + ")");
        if (tabs.getSelectedIndex() == TAB_FANIN) {
            contador.setText(Integer.toString(contadorFanIn));
        }

        feedFanIn.revalidate();
        feedFanIn.repaint();

        // 4. Solo subimos el scroll al tope si la nueva alerta es la de mayor score
        //    (ha quedado arriba del todo). Si se inserta en medio, no movemos al
        //    usuario de donde está leyendo.
        if (idx == 0) {
            scrollFanIn.getVerticalScrollBar().setValue(0);
        }
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
