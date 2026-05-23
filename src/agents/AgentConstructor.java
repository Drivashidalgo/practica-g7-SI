package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import behaviours.ConstruirGrafoBehaviour;

public class AgentConstructor extends Agent {

    /**
     * Número de aristas nuevas que disparan el envío al Analista.
     * Ajusta este valor según el tamaño del CSV de prueba.
     */
    public static final int EDGE_THRESHOLD = 30;

    /**
     * Milisegundos mínimos entre dos notificaciones consecutivas al Analista.
     * Evita inundar al Analista si las transacciones llegan en ráfaga.
     * Pon 0 para desactivar el cooldown.
     */
    public static final long COOLDOWN_MS = 10_000L;

    @Override
    protected void setup() {
        System.out.println("[AgentConstructor] Iniciando: " + getLocalName());
        registerInDF();
        addBehaviour(new ConstruirGrafoBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
        System.out.println("[AgentConstructor] Finalizado: " + getLocalName());
    }

    /**
     * Busca al AgentAnalyst en el DF por tipo "analisis-fraude".
     * Llamado desde ConstruirGrafoBehaviour cuando toca notificar.
     */
    public AID findAnalyst() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("analisis-fraude");
        template.addServices(sd);

        try {
            DFAgentDescription[] results = DFService.search(this, template);
            if (results != null && results.length > 0) {
                return results[0].getName();
            }
        } catch (FIPAException e) {
            System.err.println("[AgentConstructor] Error buscando Analista en DF: " + e.getMessage());
        }

        System.err.println("[AgentConstructor] AgentAnalyst no encontrado en el DF.");
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