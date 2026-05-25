package models;

import java.time.LocalTime;

/**
 * Modelo de una alerta de fan-in producida por AgentScoring (GNN).
 *
 * Una cuenta receptora ha sido marcada como sospechosa con una probabilidad
 * [umbral, 1.0]. El umbral lo decide el AgentScoring (RISK_THRESHOLD).
 */
public class AlertaFanIn {

    /** Severidad derivada del score, para colorear la tarjeta. */
    public enum Severidad { ALTA, MEDIA, BAJA }

    private final String cuenta;
    private final double score;
    private final LocalTime hora;

    public AlertaFanIn(String cuenta, double score, LocalTime hora) {
        if (cuenta == null || cuenta.isEmpty()) {
            throw new IllegalArgumentException("Cuenta vacía.");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score fuera de [0,1]: " + score);
        }
        this.cuenta = cuenta;
        this.score = score;
        this.hora = hora;
    }

    /**
     * Parsea el contenido ACL del AgentScoring:
     *   "fan-in;<cuenta>;<score>"
     *   Ejemplo: "fan-in;4481;0.9904"
     * Lanza IllegalArgumentException si el formato es inválido.
     */
    public static AlertaFanIn parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Contenido vacío.");
        }
        String[] parts = content.split(";");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Formato fan-in inválido: " + content);
        }
        if (!"fan-in".equalsIgnoreCase(parts[0].trim())) {
            throw new IllegalArgumentException("Prefijo no es 'fan-in': " + parts[0]);
        }
        String cuenta = parts[1].trim();
        double score = Double.parseDouble(parts[2].trim());
        return new AlertaFanIn(cuenta, score, LocalTime.now());
    }

    public String getCuenta() { return cuenta; }
    public double getScore()  { return score; }
    public LocalTime getHora() { return hora; }

    public Severidad getSeveridad() {
        if (score >= 0.9) return Severidad.ALTA;
        if (score >= 0.8) return Severidad.MEDIA;
        return Severidad.BAJA;
    }
}
