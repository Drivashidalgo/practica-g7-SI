package models;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelo de un ciclo de fraude consumido por la UI.
 *
 * Invariante: arista i va de cuentas[i] -> cuentas[(i+1) % N],
 * con importes[i] como importe de esa arista.
 */
public class CicloFraude {

    private final int numero;
    private final List<String> cuentas;
    private final List<Double> importes;
    private final double total;
    private final LocalTime hora;

    public CicloFraude(int numero, List<String> cuentas, List<Double> importes, LocalTime hora) {
        if (cuentas == null || importes == null || cuentas.size() != importes.size() || cuentas.isEmpty()) {
            throw new IllegalArgumentException("Cuentas e importes deben ser no vacíos y del mismo tamaño.");
        }
        this.numero = numero;
        this.cuentas = Collections.unmodifiableList(new ArrayList<>(cuentas));
        this.importes = Collections.unmodifiableList(new ArrayList<>(importes));
        double s = 0.0;
        for (Double d : importes) s += d;
        this.total = s;
        this.hora = hora;
    }

    /**
     * Parsea el contenido ACL del AgentAnalyst:
     *   "C429->C871:5000;C871->C512:4800;C512->C429:4500"
     * Lanza IllegalArgumentException si el formato es inválido.
     */
    public static CicloFraude parse(String content, int numero) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Contenido vacío.");
        }
        String[] aristas = content.split(";");
        if (aristas.length < 2) {
            throw new IllegalArgumentException("Un ciclo debe tener al menos 2 aristas.");
        }

        List<String> cuentas = new ArrayList<>();
        List<Double> importes = new ArrayList<>();
        String primerOrigen = null;
        String ultimoDestino = null;

        for (int i = 0; i < aristas.length; i++) {
            String trozo = aristas[i].trim();
            if (trozo.isEmpty()) {
                throw new IllegalArgumentException("Arista vacía en posición " + i);
            }
            int flecha = trozo.indexOf("->");
            int dosp = trozo.lastIndexOf(":");
            if (flecha < 0 || dosp < 0 || dosp < flecha + 2) {
                throw new IllegalArgumentException("Arista mal formada: " + trozo);
            }
            String origen = trozo.substring(0, flecha).trim();
            String destino = trozo.substring(flecha + 2, dosp).trim();
            String importeTxt = trozo.substring(dosp + 1).trim();
            if (origen.isEmpty() || destino.isEmpty()) {
                throw new IllegalArgumentException("Origen o destino vacío en arista: " + trozo);
            }
            double importe = Double.parseDouble(importeTxt);

            if (i == 0) {
                primerOrigen = origen;
            } else if (!origen.equals(ultimoDestino)) {
                throw new IllegalArgumentException(
                    "Cadena rota en arista " + i + ": esperado origen=" + ultimoDestino + " pero llegó " + origen);
            }
            cuentas.add(origen);
            importes.add(importe);
            ultimoDestino = destino;
        }

        if (!ultimoDestino.equals(primerOrigen)) {
            throw new IllegalArgumentException(
                "El ciclo no cierra: último destino " + ultimoDestino + " != primer origen " + primerOrigen);
        }

        return new CicloFraude(numero, cuentas, importes, LocalTime.now());
    }

    public int getNumero() { return numero; }
    public List<String> getCuentas() { return cuentas; }
    public List<Double> getImportes() { return importes; }
    public double getTotal() { return total; }
    public LocalTime getHora() { return hora; }

    public int getNumNodos() { return cuentas.size(); }
}
