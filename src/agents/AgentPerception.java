package agents;

import behaviours.LeerCSVLiveBehaviour;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

/**
 * Agente de percepción: lee transacciones en vivo del CSV y las reenvía al Constructor.
 *
 * También informa al AgentUI del estado del stream para alimentar el indicador visual:
 *   - 5 segundos tras el arranque  → envía "INICIO" y comienza la lectura del CSV.
 *   - Tras detectar fin de la lectura, espera 15 segundos → envía "FIN".
 *     (Ese margen da tiempo a que Analyst y Scoring entreguen sus últimas alertas
 *      antes de que la UI marque la sesión como cerrada.)
 *
 * Contrato con AgentUI:
 *   performative = INFORM
 *   conversationId = "estado-stream"
 *   content = "INICIO" | "FIN"
 *
 * Resolución del receptor: se busca en el DF por ServiceType="interfaz-visualizacion"
 * (mismo patrón que usan AgentAnalyst y AgentScoring) en lugar de fiarse del local name.
 */
public class AgentPerception extends Agent {

    /** Tiempo desde el arranque hasta enviar "INICIO" y empezar a leer el CSV. */
    private static final long DELAY_INICIO_MS = 5_000L;

    /** Tiempo entre detectar fin de lectura y enviar "FIN" a la UI. */
    private static final long DELAY_FIN_MS = 15_000L;

    /**
     * Ticks consecutivos sin líneas nuevas (tras haber procesado al menos una)
     * para considerar que el stream ha terminado. Con intervalo de 1 s, son
     * 5 s de inactividad.
     */
    private static final int INACTIVIDAD_TICKS = 5;

    /** Tipo de servicio bajo el que AgentUI se registra en el DF. */
    private static final String UI_SERVICE_TYPE = "interfaz-visualizacion";

    @Override
    protected void setup() {
        System.out.println("Agente de Percepción iniciado: " + getLocalName());

        final String archivoLive = "data/transactions_live.csv";
        final String agenteConstructor = "constructor";
        final long intervaloRevision = 1000;

        // 1) Esperar 5 s, enviar INICIO y arrancar la lectura del CSV.
        addBehaviour(new WakerBehaviour(this, DELAY_INICIO_MS) {
            @Override
            protected void onWake() {
                enviarEstado("INICIO");
                addBehaviour(new LeerCSVLiveBehaviour(
                        myAgent,
                        intervaloRevision,
                        archivoLive,
                        agenteConstructor,
                        INACTIVIDAD_TICKS,
                        () -> programarFin()
                ));
            }
        });
    }

    /** Llamado por el LeerCSVLiveBehaviour cuando detecta fin del stream. */
    private void programarFin() {
        System.out.println("[AgentPerception] Fin de lectura detectado. "
                + "Programando FIN en " + (DELAY_FIN_MS / 1000) + " s.");
        addBehaviour(new WakerBehaviour(this, DELAY_FIN_MS) {
            @Override
            protected void onWake() {
                enviarEstado("FIN");
            }
        });
    }

    private void enviarEstado(String estado) {
        AID uiAID = buscarUIenDF();
        if (uiAID == null) {
            System.err.println("[AgentPerception] AgentUI no encontrado en el DF. "
                    + "Estado '" + estado + "' no entregado.");
            return;
        }

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(uiAID);
        msg.setConversationId("estado-stream");
        msg.setContent(estado);
        send(msg);
        System.out.println("[AgentPerception] Estado enviado a "
                + uiAID.getLocalName() + ": " + estado);
    }

    /** Busca en el DF un agente con ServiceType="interfaz-visualizacion". */
    private AID buscarUIenDF() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(UI_SERVICE_TYPE);
        template.addServices(sd);
        try {
            DFAgentDescription[] resultados = DFService.search(this, template);
            if (resultados != null && resultados.length > 0) {
                return resultados[0].getName();
            }
        } catch (FIPAException e) {
            System.err.println("[AgentPerception] Error buscando UI en DF: " + e.getMessage());
        }
        return null;
    }
}
