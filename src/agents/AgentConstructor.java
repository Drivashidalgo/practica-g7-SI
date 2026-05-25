package agents;

import behaviours.ConstruirGrafoBehaviour;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.core.Agent;

public class AgentConstructor extends Agent {

    public static final int    EDGE_THRESHOLD = 1000;
    public static final long   COOLDOWN_MS    = 0L;

    @Override
    protected void setup() {
        System.out.println("[AgentConstructor] Iniciando: " + getLocalName());
        registerInDF();
        addBehaviour(new ConstruirGrafoBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[AgentConstructor] Finalizado: " + getLocalName());
    }

    /**
     * Busca un agente en el DF por tipo de servicio.
     * Usado por ConstruirGrafoBehaviour para localizar AgentAnalyst y AgentScoring.
     */
    public AID findAgentByType(String serviceType) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(this, template);
            if (results != null && results.length > 0) return results[0].getName();
        } catch (FIPAException e) {
            System.err.println("[AgentConstructor] DF error buscando " + serviceType + ": " + e.getMessage());
        }
        return null;
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("constructor-grafo");
        sd.setType("construccion-grafo");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[AgentConstructor] Registrado en el DF.");
        } catch (FIPAException e) {
            System.err.println("[AgentConstructor] Error al registrar en el DF: " + e.getMessage());
        }
    }
}