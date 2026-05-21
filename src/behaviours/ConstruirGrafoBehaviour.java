package behaviours;

import agents.AgentConstructor;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour principal de AgentConstructor.
 *
 * Escucha INFORM con conversationId="transaccion-bancaria" de AgentPerception.
 * Construye un grafo dirigido con JGraphT.
 *
 * Cada EDGE_THRESHOLD aristas nuevas notifica a DOS agentes:
 *   - AgentAnalyst  (conversationId="grafo-listo")   → detecta ciclos con Tarjan
 *   - AgentScoring  (conversationId="grafo-scoring")  → detecta fan-in con ML
 *
 * Formato mensaje recibido:
 *   TX_ID;SENDER;RECEIVER;TX_TYPE;TX_AMOUNT;TIMESTAMP
 *
 * Formato mensaje enviado (mismo, una transacción por línea):
 *   TX_ID;SENDER;RECEIVER;TX_TYPE;TX_AMOUNT;TIMESTAMP\n...
 */
public class ConstruirGrafoBehaviour extends CyclicBehaviour {

    private final DefaultDirectedGraph<String, DefaultEdge> graph =
            new DefaultDirectedGraph<>(DefaultEdge.class);

    private final List<String> allTransactions = new ArrayList<>();

    private int  edgesSinceLastNotification = 0;
    private long lastNotificationTime       = 0L;

    private static final MessageTemplate TEMPLATE = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("transaccion-bancaria")
    );

    public ConstruirGrafoBehaviour(AgentConstructor agent) {
        super(agent);
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive(TEMPLATE);
        if (msg == null) { block(); return; }

        String content = msg.getContent();
        if (content == null || content.isBlank()) return;

        processTransaction(content.trim());
        maybeNotifyAgents();
    }

    private void processTransaction(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length < 3) {
            System.err.println("[ConstruirGrafoBehaviour] Línea malformada: " + line);
            return;
        }

        String txId    = parts[0].trim();
        String sender  = parts[1].trim();
        String receiver= parts[2].trim();

        if (sender.isEmpty() || receiver.isEmpty()) return;

        graph.addVertex(sender);
        graph.addVertex(receiver);

        boolean added = graph.addEdge(sender, receiver) != null;
        if (added) edgesSinceLastNotification++;

        allTransactions.add(line);

        System.out.printf("[ConstruirGrafoBehaviour] TX=%s (%s→%s) | vértices=%d aristas=%d nuevas=%d%n",
                txId, sender, receiver,
                graph.vertexSet().size(),
                graph.edgeSet().size(),
                edgesSinceLastNotification);
    }

    private void maybeNotifyAgents() {
        if (edgesSinceLastNotification < AgentConstructor.EDGE_THRESHOLD) return;

        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < AgentConstructor.COOLDOWN_MS) return;

        // Construir payload: todas las transacciones históricas
        StringBuilder payload = new StringBuilder();
        for (String tx : allTransactions) {
            payload.append(tx).append("\n");
        }
        String content = payload.toString();

        // Notificar a AgentAnalyst (ciclos)
        AID analystAID = ((AgentConstructor) myAgent).findAgentByType("analisis-fraude");
        if (analystAID != null) {
            ACLMessage msgAnalyst = new ACLMessage(ACLMessage.INFORM);
            msgAnalyst.addReceiver(analystAID);
            msgAnalyst.setConversationId("grafo-listo");
            msgAnalyst.setContent(content);
            myAgent.send(msgAnalyst);
            System.out.println("[ConstruirGrafoBehaviour] Grafo enviado a AgentAnalyst (ciclos).");
        } else {
            System.err.println("[ConstruirGrafoBehaviour] AgentAnalyst no encontrado en el DF.");
        }

        // Notificar a AgentScoring (fan-in ML)
        AID scoringAID = ((AgentConstructor) myAgent).findAgentByType("scoring-fraude");
        if (scoringAID != null) {
            ACLMessage msgScoring = new ACLMessage(ACLMessage.INFORM);
            msgScoring.addReceiver(scoringAID);
            msgScoring.setConversationId("grafo-scoring");
            msgScoring.setContent(content);
            myAgent.send(msgScoring);
            System.out.println("[ConstruirGrafoBehaviour] Grafo enviado a AgentScoring (fan-in).");
        } else {
            System.err.println("[ConstruirGrafoBehaviour] AgentScoring no encontrado en el DF.");
        }

        edgesSinceLastNotification = 0;
        lastNotificationTime = now;
    }
}