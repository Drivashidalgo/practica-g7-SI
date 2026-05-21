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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

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

        System.out.println("[CalcularScoringBehaviour] Grafo recibido, analizando fan-in...");

        // 1. Volcar grafo a archivo temporal
        try {
            Files.writeString(Paths.get(AgentScoring.GRAPH_TMP_FILE), grafoContent);
        } catch (IOException e) {
            System.err.println("[CalcularScoringBehaviour] Error escribiendo grafo tmp: " + e.getMessage());
            return;
        }

        System.out.println("[CalcularScoringBehaviour] Directorio de trabajo: " + System.getProperty("user.dir"));
        System.out.println("[CalcularScoringBehaviour] Script: " + AgentScoring.SCORE_SCRIPT);
        System.out.println("[CalcularScoringBehaviour] Grafo tmp: " + AgentScoring.GRAPH_TMP_FILE);

        // 2. Llamar a Python
        String resultado = callPython(AgentScoring.GRAPH_TMP_FILE);

        System.out.println("[CalcularScoringBehaviour] Python output: '" + resultado + "'");

        if (resultado == null || resultado.equals("NONE") || resultado.startsWith("ERROR")) {
            System.out.println("[CalcularScoringBehaviour] Sin nodos fan-in de alto riesgo.");
            return;
        }

        // 3. Procesar resultados y notificar UI
        AID uiAID = findAgent("interfaz-usuario");
        for (String linea : resultado.split("\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            String[] parts = linea.split(";");
            if (parts.length < 2) continue;

            String receiver = parts[0];
            String score    = parts[1];

            System.out.printf("[CalcularScoringBehaviour] ⚠ Fan-in ALTO RIESGO: cuenta=%s  score=%s%n",
                    receiver, score);

            if (uiAID != null) {
                ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
                alert.addReceiver(uiAID);
                alert.setConversationId("alerta-fraude");
                alert.setContent("fan-in;" + receiver + ";" + score);
                myAgent.send(alert);
            }
        }
    }

    private String callPython(String graphFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    AgentScoring.PYTHON_CMD,
                    AgentScoring.SCORE_SCRIPT,
                    graphFile
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

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                System.err.println("[CalcularScoringBehaviour] Python timeout - proceso terminado");
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
            System.err.println("[CalcularScoringBehaviour] DF error: " + e.getMessage());
        }
        return null;
    }
}