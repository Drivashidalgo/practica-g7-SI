package agents;

import behaviours.CalcularScoringBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

/**
 * AgentScoring — detecta fraude tipo fan-in usando una red neuronal de grafos (GNN).
 *
 * Flujo:
 *   1. Recibe el grafo acumulado de AgentConstructor (conversationId="grafo-scoring").
 *   2. Vuelca el grafo a un archivo temporal.
 *   3. Invoca el script Python score_fanin_gnn.py, que:
 *        - Carga model_fanin.pt (modelo GNN entrenado offline).
 *        - Aplica inferencia sobre el grafo recibido.
 *        - Devuelve por stdout las cuentas con probabilidad >= RISK_THRESHOLD.
 *   4. Notifica a AgentUI con cada cuenta de alto riesgo (conversationId="alerta-fraude").
 *
 * Reparto de responsabilidades con AgentAnalyst:
 *   - AgentAnalyst → ciclos (Tarjan, búsqueda exacta).
 *   - AgentScoring → fan-in (GNN, clasificación probabilística).
 */
public class AgentScoring extends Agent {

    /**
     * Probabilidad mínima para alertar de una cuenta como fan-in.
     * Calibrado con evaluate_streaming.py sobre datos no vistos:
     * a 0.5 da precision 0.81 y recall 0.47.
     * Variar este valor entre 0.2 y 0.7 cambia las métricas marginalmente
     * (el modelo produce probabilidades polarizadas, sin zona gris útil).
     */
    public static final double RISK_THRESHOLD = 0.5;

    /**
     * Intérprete Python en el PATH.
     * Windows: "python" o "py".  Linux/Mac: "python3".
     */
    public static final String PYTHON_CMD = "C:\\Users\\jlope\\anaconda3\\envs\\tensorflow\\python.exe";

    /** Script de inferencia (ruta relativa a la raíz del proyecto). */
    public static final String SCORE_SCRIPT = "data/score_fanin_gnn.py";

    /** Archivo temporal donde se vuelca el grafo para pasárselo a Python. */
    public static final String GRAPH_TMP_FILE = "data/grafo_tmp.txt";

    /** Timeout máximo de la inferencia Python (segundos). */
    public static final int PYTHON_TIMEOUT_SEC = 30;

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
        sd.setName("scoring-fanin-gnn");
        sd.setType("scoring-fraude");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[AgentScoring] Registrado en el DF (tipo: scoring-fraude).");
        } catch (FIPAException e) {
            System.err.println("[AgentScoring] Error al registrar en el DF: " + e.getMessage());
        }
    }
}
