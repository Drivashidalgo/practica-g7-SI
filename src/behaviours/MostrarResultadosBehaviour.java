package behaviours;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import models.AlertaFanIn;
import models.CicloFraude;
import ui.MonitorFrame;

import javax.swing.SwingUtilities;

/**
 * Escucha en bucle el buzón del AgentUI:
 *   - "alerta-fraude" (AgentAnalyst): ciclos de blanqueo (Tarjan).
 *   - "alerta-fraude" (AgentScoring): cuentas fan-in (GNN) — prefijo "fan-in;".
 *   - "estado-stream" (AgentPerception): "INICIO" | "FIN".
 *
 * Ambos remitentes usan el mismo conversationId. Distinguimos por el prefijo
 * del contenido: si empieza por "fan-in;" es del Scoring, si no es un ciclo.
 *
 * Filtra con MessageTemplate (performativa INFORM + uno de los dos ConversationId).
 * Toda mutación de la UI se delega al EDT con SwingUtilities.invokeLater.
 */
public class MostrarResultadosBehaviour extends CyclicBehaviour {

    private final MonitorFrame frame;
    private final MessageTemplate filtro;
    private int contadorCiclos = 0;

    public MostrarResultadosBehaviour(Agent a, MonitorFrame frame) {
        super(a);
        this.frame = frame;
        this.filtro = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.or(
                        MessageTemplate.MatchConversationId("alerta-fraude"),
                        MessageTemplate.MatchConversationId("estado-stream")
                )
        );
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive(filtro);
        if (msg == null) {
            block();
            return;
        }

        String conv = msg.getConversationId();
        String content = msg.getContent();

        if ("estado-stream".equals(conv)) {
            manejarEstado(content);
        } else if ("alerta-fraude".equals(conv)) {
            manejarAlerta(content);
        }
    }

    private void manejarEstado(String content) {
        if (content == null) return;
        String estado = content.trim().toUpperCase();
        switch (estado) {
            case "INICIO":
                System.out.println("[AgentUI] Estado: INICIO");
                SwingUtilities.invokeLater(frame::setEstadoMonitorizando);
                break;
            case "FIN":
                System.out.println("[AgentUI] Estado: FIN");
                SwingUtilities.invokeLater(frame::setEstadoFinalizado);
                break;
            default:
                System.err.println("[AgentUI] Estado desconocido: " + content);
        }
    }

    private void manejarAlerta(String content) {
        if (content == null) return;
        // El AgentScoring marca sus mensajes con el prefijo "fan-in;".
        // Cualquier otra cosa la tratamos como un ciclo del AgentAnalyst.
        if (content.trim().toLowerCase().startsWith("fan-in;")) {
            manejarFanIn(content);
        } else {
            manejarCiclo(content);
        }
    }

    private void manejarCiclo(String content) {
        int numero = contadorCiclos + 1;
        CicloFraude ciclo;
        try {
            ciclo = CicloFraude.parse(content, numero);
        } catch (RuntimeException e) {
            System.err.println("[AgentUI] Ciclo descartado (formato inválido): "
                    + e.getMessage() + " | contenido='" + content + "'");
            return;
        }
        contadorCiclos = numero;
        System.out.println("[AgentUI] Ciclo #" + numero + " recibido: "
                + ciclo.getCuentas() + " total=" + ciclo.getTotal());
        SwingUtilities.invokeLater(() -> frame.addAlerta(ciclo));
    }

    private void manejarFanIn(String content) {
        AlertaFanIn alerta;
        try {
            alerta = AlertaFanIn.parse(content);
        } catch (RuntimeException e) {
            System.err.println("[AgentUI] Fan-in descartado (formato inválido): "
                    + e.getMessage() + " | contenido='" + content + "'");
            return;
        }
        System.out.println("[AgentUI] Fan-in: cuenta=" + alerta.getCuenta()
                + " score=" + alerta.getScore());
        SwingUtilities.invokeLater(() -> frame.addFanIn(alerta));
    }
}
