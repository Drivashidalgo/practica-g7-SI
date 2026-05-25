package behaviours;

import agents.AgentScoring;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Behaviour principal de AgentScoring.
 *
 * Espera mensajes INFORM con conversationId="grafo-scoring" desde AgentConstructor.
 * Por cada grafo recibido:
 *   1. Lo vuelca a un archivo temporal.
 *   2. Invoca el script Python de inferencia GNN, pasándole también el threshold.
 *   3. Procesa la salida y notifica a AgentUI con cada cuenta de alto riesgo.
 *
 * Formato grafo recibido (una transacción por línea):
 *   TX_ID;SENDER;RECEIVER;TX_TYPE;TX_AMOUNT;TIMESTAMP
 *
 * Formato salida Python (una cuenta alertada por línea):
 *   account_id;score
 * O bien "NONE" si no hay alertas; "ERROR: ..." si algo falla.
 *
 * Formato mensaje enviado a AgentUI:
 *   fan-in;account_id;score
 */
public class CalcularScoringBehaviour extends CyclicBehaviour {

    private static final MessageTemplate TEMPLATE = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("grafo-scoring")
    );

    public CalcularScoringBehaviour(AgentScoring agent) {
        super(agent);
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive(TEMPLATE);

        if (msg == null) {
            block();
            return;
        }

        String grafoContent = msg.getContent();
        if (grafoContent == null || grafoContent.isBlank()) return;

        System.out.println("[CalcularScoringBehaviour] Grafo recibido, ejecutando inferencia GNN...");

        // 1. Volcar grafo a archivo temporal
        try {
            Files.writeString(Paths.get(AgentScoring.GRAPH_TMP_FILE), grafoContent);
        } catch (IOException e) {
            System.err.println("[CalcularScoringBehaviour] Error escribiendo grafo tmp: " + e.getMessage());
            return;
        }

        // 2. Invocar Python pasando el threshold como argumento
        String resultado = callPython(AgentScoring.GRAPH_TMP_FILE, AgentScoring.RISK_THRESHOLD);

        if (resultado == null) {
            System.err.println("[CalcularScoringBehaviour] Sin respuesta del script Python.");
            return;
        }

        if (resultado.equals("NONE")) {
            System.out.println("[CalcularScoringBehaviour] Sin cuentas fan-in de alto riesgo.");
            return;
        }

        if (resultado.startsWith("ERROR")) {
            System.err.println("[CalcularScoringBehaviour] Error en script Python: " + resultado);
            return;
        }

        // 3. Procesar resultados y notificar UI
        procesarResultados(resultado);
    }

    private void procesarResultados(String resultado) {
        AID uiAID = findAgent("interfaz-visualizacion");
        int alertas = 0;

        for (String linea : resultado.split("\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            String[] parts = linea.split(";");
            if (parts.length < 2) continue;

            String receiver = parts[0].trim();
            String score    = parts[1].trim();

            System.out.printf("[CalcularScoringBehaviour] ⚠ Fan-in detectado: cuenta=%s  score=%s%n",
                    receiver, score);
            alertas++;

            if (uiAID != null) {
                ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
                alert.addReceiver(uiAID);
                alert.setConversationId("alerta-fraude");
                alert.setContent("fan-in;" + receiver + ";" + score);
                myAgent.send(alert);
            }
        }

        if (uiAID == null && alertas > 0) {
            System.err.println("[CalcularScoringBehaviour] AgentUI no encontrado en el DF — "
                    + alertas + " alertas no entregadas.");
        } else if (alertas > 0) {
            System.out.println("[CalcularScoringBehaviour] " + alertas
                    + " alertas fan-in enviadas a AgentUI.");
        }
    }

    private String callPython(String graphFile, double threshold) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    AgentScoring.PYTHON_CMD,
                    AgentScoring.SCORE_SCRIPT,
                    graphFile,
                    String.valueOf(threshold)
            );
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(AgentScoring.PYTHON_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                System.err.println("[CalcularScoringBehaviour] Python timeout ("
                        + AgentScoring.PYTHON_TIMEOUT_SEC + "s) - proceso terminado");
                return null;
            }

            return sb.toString().trim();

        } catch (Exception e) {
            System.err.println("[CalcularScoringBehaviour] Error llamando Python: " + e.getMessage());
            return null;
        }
    }

    private AID findAgent(String serviceType) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(myAgent, template);
            if (results != null && results.length > 0) return results[0].getName();
        } catch (FIPAException e) {
            System.err.println("[CalcularScoringBehaviour] DF error buscando "
                    + serviceType + ": " + e.getMessage());
        }
        return null;
    }
}
