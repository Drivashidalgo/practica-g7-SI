package behaviours;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import models.CicloFraude;
import ui.MonitorFrame;

import javax.swing.SwingUtilities;

/**
 * Escucha en bucle el buzón del AgentUI:
 *   - "alerta-fraude" (del AgentAnalyst): un ciclo por mensaje.
 *   - "estado-stream" (del AgentPerception): "INICIO" | "FIN".
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
        int numero = contadorCiclos + 1;
        CicloFraude ciclo;
        try {
            ciclo = CicloFraude.parse(content, numero);
        } catch (RuntimeException e) {
            System.err.println("[AgentUI] Alerta descartada (formato inválido): "
                    + e.getMessage() + " | contenido='" + content + "'");
            return;
        }
        contadorCiclos = numero;
        System.out.println("[AgentUI] Ciclo #" + numero + " recibido: "
                + ciclo.getCuentas() + " total=" + ciclo.getTotal());
        SwingUtilities.invokeLater(() -> frame.addAlerta(ciclo));
    }
}
