package ui;

import java.awt.Color;

/**
 * Paleta dark "Security Operations Center" usada por toda la UI.
 * Swing no hereda tema del SO, así que fijamos los colores aquí.
 */
public final class Paleta {

    private Paleta() {}

    public static final Color FONDO              = new Color(0x131722);
    public static final Color FONDO_CABECERA     = new Color(0x10151C);
    public static final Color CARD_BG            = new Color(0x161C24);
    public static final Color BORDE_ALERTA       = new Color(0xF85149);

    public static final Color TEXTO_PRINCIPAL    = new Color(0xE8EAED);
    public static final Color TEXTO_SECUNDARIO   = new Color(0x8B949E);

    public static final Color ACENTO_ESPERA      = new Color(0xD29922);
    public static final Color ACENTO_ACTIVO      = new Color(0x3FB950);
    public static final Color ACENTO_FIN         = new Color(0x58A6FF);

    public static final Color IMPORTE            = new Color(0xF0883E);

    public static final Color NODO_ORIGEN        = new Color(0xF85149);
    public static final Color NODO_NORMAL        = new Color(0x58A6FF);
    public static final Color ARISTA             = new Color(0xC9D1D9);
}
