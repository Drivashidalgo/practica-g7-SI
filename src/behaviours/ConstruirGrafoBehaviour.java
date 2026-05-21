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
 * Escucha mensajes INFORM con conversationId="transaccion-bancaria"
 * enviados por AgentPerception. Cada mensaje contiene UNA transacción
 * en el formato que produce Transaccion.toMessageContent():
 *
 *   TX_ID;SENDER_ACCOUNT_ID;RECEIVER_ACCOUNT_ID;TX_TYPE;TX_AMOUNT;TIMESTAMP
 *
 * Nota: el CSV original usa comas, pero CSVUtils parsea las columnas y
 * Transaccion.toMessageContent() reensambla con punto y coma (;).
 * Por tanto aquí se parsea con split(";").
 *
 * Cada vez que se acumulan EDGE_THRESHOLD aristas nuevas (y ha pasado
 * el cooldown), se envía al AgentAnalyst un INFORM con conversationId
 * "grafo-listo" cuyo contenido son TODAS las transacciones acumuladas,
 * una por línea, en el mismo formato separado por punto y coma.
 */
public class ConstruirGrafoBehaviour extends CyclicBehaviour {

    // Grafo dirigido: nodos = cuentas, aristas = transferencias
    private final DefaultDirectedGraph<String, DefaultEdge> graph =
            new DefaultDirectedGraph<>(DefaultEdge.class);

    // Historial completo de transacciones válidas recibidas
    private final List<String> allTransactions = new ArrayList<>();

    // Aristas nuevas desde la última notificación al Analista
    private int edgesSinceLastNotification = 0;

    // Marca de tiempo de la última notificación enviada
    private long lastNotificationTime = 0L;

    // Filtro bloqueante: solo INFORM con conversationId correcto
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

        if (msg == null) {
            block(); // cede el hilo, no hace busy-waiting
            return;
        }

        String content = msg.getContent();
        if (content == null || content.isBlank()) {
            return;
        }

        processTransaction(content.trim());
        maybeNotifyAnalyst();
    }

    /**
     * Parsea la línea con ";" (formato de Transaccion.toMessageContent()),
     * añade los nodos y la arista al grafo.
     */
    private void processTransaction(String line) {
        // Formato: TX_ID;SENDER;RECEIVER;TX_TYPE;TX_AMOUNT;TIMESTAMP
        String[] parts = line.split(";", -1);

        if (parts.length < 3) {
            System.err.println("[ConstruirGrafoBehaviour] Línea malformada, ignorada: " + line);
            return;
        }

        String txId    = parts[0].trim();
        String sender  = parts[1].trim();
        String receiver = parts[2].trim();

        if (sender.isEmpty() || receiver.isEmpty()) {
            System.err.println("[ConstruirGrafoBehaviour] Sender o Receiver vacío, ignorado: " + line);
            return;
        }

        // addVertex no hace nada si el nodo ya existe (idempotente en JGraphT)
        graph.addVertex(sender);
        graph.addVertex(receiver);

        // DefaultDirectedGraph ignora aristas duplicadas (mismo par sender->receiver)
        boolean added = graph.addEdge(sender, receiver) != null;

        if (added) {
            edgesSinceLastNotification++;
        }

        allTransactions.add(line);

        System.out.printf("[ConstruirGrafoBehaviour] TX recibida: %s  (%s → %s) | " +
                        "vértices=%d  aristas=%d  nuevas_desde_última_notif=%d%n",
                txId, sender, receiver,
                graph.vertexSet().size(),
                graph.edgeSet().size(),
                edgesSinceLastNotification);
    }

    /**
     * Envía el grafo al Analista si se han acumulado suficientes aristas nuevas
     * y ha pasado el cooldown desde la última notificación.
     */
    private void maybeNotifyAnalyst() {
        if (edgesSinceLastNotification < AgentConstructor.EDGE_THRESHOLD) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < AgentConstructor.COOLDOWN_MS) {
            return;
        }

        AID analystAID = ((AgentConstructor) myAgent).findAnalyst();
        if (analystAID == null) {
            // El Analista aún no está registrado; lo intentará en la próxima TX
            return;
        }

        // Payload: todas las transacciones históricas, una por línea
        StringBuilder payload = new StringBuilder();
        for (String tx : allTransactions) {
            payload.append(tx).append("\n");
        }

        ACLMessage notify = new ACLMessage(ACLMessage.INFORM);
        notify.addReceiver(analystAID);
        notify.setConversationId("grafo-listo");
        notify.setContent(payload.toString());
        myAgent.send(notify);

        System.out.printf("[ConstruirGrafoBehaviour] Grafo enviado al Analista — " +
                        "%d transacciones totales, %d aristas en el grafo.%n",
                allTransactions.size(),
                graph.edgeSet().size());

        edgesSinceLastNotification = 0;
        lastNotificationTime = now;
    }
}