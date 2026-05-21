package agents;

import behaviours.CalcularScoringBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

/**
 * AgentScoring — detecta fraude tipo fan-in usando ML sobre el grafo.
 *
 * Recibe el grafo acumulado de AgentConstructor (conversationId="grafo-scoring"),
 * llama al script Python score_fanin.py que extrae features estructurales
 * del grafo y aplica el modelo entrenado, y notifica a AgentUI con
 * las cuentas de alto riesgo (conversationId="alerta-fraude").
 *
 * AgentAnalyst se encarga de los ciclos (Tarjan).
 * Este agente se encarga del fan-in (ML).
 */
public class AgentScoring extends Agent {

    /** Umbral de probabilidad para considerar fan-in fraudulento (0.0-1.0). */
    public static final double RISK_THRESHOLD = 0.7;

    /**
     * Comando Python. Ajusta si no está en el PATH.
     * Windows: "python" o "py"
     * Linux/Mac: "python3"
     */
    public static final String PYTHON_CMD = "python";

    /** Script de scoring relativo a la raíz del proyecto. */
    public static final String SCORE_SCRIPT = "data/score_fanin.py";

    /** Archivo temporal donde se vuelca el grafo para pasárselo a Python. */
    public static final String GRAPH_TMP_FILE = "data/grafo_tmp.txt";

    @Override
    protected void setup() {
        System.out.println("[AgentScoring] Iniciando: " + getLocalName());
        registerInDF();
        addBehaviour(new CalcularScoringBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
        System.out.println("[AgentScoring] Finalizado: " + getLocalName());
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setName("scoring-fraude");
        sd.setType("scoring-fraude");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[AgentScoring] Registrado en el DF.");
        } catch (FIPAException e) {
            System.err.println("[AgentScoring] Error al registrar en el DF: " + e.getMessage());
        }
    }
}