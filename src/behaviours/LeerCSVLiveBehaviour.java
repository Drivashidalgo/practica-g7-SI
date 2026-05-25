package behaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import models.Transaccion;
import utils.CSVUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Lee un CSV "en vivo" — cada tick relee el fichero y emite las líneas nuevas.
 *
 * Detección de fin de stream por inactividad: cuando tras haber procesado al menos
 * una línea pasan {@code umbralInactividad} ticks consecutivos sin líneas nuevas,
 * se considera que el stream ha terminado: se detiene el ticker y se invoca el
 * callback {@code onStreamFinished} para que el agente notifique a la UI.
 */
public class LeerCSVLiveBehaviour extends TickerBehaviour {

    private final String archivoLive;
    private final String agenteDestino;
    private final int umbralInactividad;
    private final Runnable onStreamFinished;

    private int lineasLeidas = 0;
    private boolean haLeidoAlgo = false;
    private int ticksConsecutivosSinDatos = 0;
    private boolean finNotificado = false;

    public LeerCSVLiveBehaviour(Agent agente, long intervaloMs, String archivoLive,
                                String agenteDestino, int umbralInactividad,
                                Runnable onStreamFinished) {
        super(agente, intervaloMs);
        this.archivoLive = archivoLive;
        this.agenteDestino = agenteDestino;
        this.umbralInactividad = umbralInactividad;
        this.onStreamFinished = onStreamFinished;
    }

    @Override
    protected void onTick() {
        if (finNotificado) return;

        int lineasNuevasEsteTick = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(archivoLive))) {
            String linea;
            int numeroLinea = 0;

            while ((linea = reader.readLine()) != null) {
                if (numeroLinea > lineasLeidas) {
                    procesarLinea(linea);
                    lineasNuevasEsteTick++;
                }
                numeroLinea++;
            }

            lineasLeidas = numeroLinea - 1;

        } catch (IOException e) {
            // Si el archivo todavía no existe, no contamos como inactividad:
            // el stream aún no ha empezado.
            System.err.println("Esperando archivo live: " + archivoLive);
            return;
        }

        if (lineasNuevasEsteTick > 0) {
            haLeidoAlgo = true;
            ticksConsecutivosSinDatos = 0;
            return;
        }

        // Tick sin líneas nuevas: solo cuenta como "inactividad" si ya leímos algo
        // alguna vez. Así evitamos disparar FIN antes de que llegue la primera línea.
        if (haLeidoAlgo) {
            ticksConsecutivosSinDatos++;
            if (ticksConsecutivosSinDatos >= umbralInactividad) {
                finNotificado = true;
                stop();
                System.out.println("[LeerCSVLiveBehaviour] Stream sin datos en "
                        + umbralInactividad + " ticks consecutivos. Fin de la lectura.");
                if (onStreamFinished != null) {
                    onStreamFinished.run();
                }
            }
        }
    }

    private void procesarLinea(String linea) {
        Transaccion transaccion = CSVUtils.parsearTransaccion(linea);

        if (transaccion == null) {
            System.err.println("Transacción inválida ignorada: " + linea);
            return;
        }

        enviarTransaccion(transaccion);
    }

    private void enviarTransaccion(Transaccion transaccion) {
        ACLMessage mensaje = new ACLMessage(ACLMessage.INFORM);

        mensaje.addReceiver(new AID(agenteDestino, AID.ISLOCALNAME));
        mensaje.setConversationId("transaccion-bancaria");
        mensaje.setContent(transaccion.toMessageContent());

        myAgent.send(mensaje);

        System.out.println("Transacción enviada al constructor: " + transaccion.toMessageContent());
    }
}
