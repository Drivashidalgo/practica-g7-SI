package agents;

import behaviours.MostrarResultadosBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import ui.MonitorFrame;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitor visual antifraude. Agente pasivo: solo escucha y refleja.
 * No envía mensajes. La ventana Swing se arranca desde setup().
 */
public class AgentUI extends Agent {

    private MonitorFrame frame;

    @Override
    protected void setup() {
        System.out.println("[AgentUI] Iniciando: " + getLocalName());
        registerInDF();

        // Arrancamos la ventana en el EDT y esperamos a tener la referencia
        // antes de añadir el behaviour, para que jamás reciba un mensaje con frame=null.
        final AtomicReference<MonitorFrame> ref = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                MonitorFrame f = new MonitorFrame();
                f.setVisible(true);
                ref.set(f);
            });
        } catch (Exception e) {
            System.err.println("[AgentUI] Error arrancando la ventana: " + e.getMessage());
            doDelete();
            return;
        }
        this.frame = ref.get();

        addBehaviour(new MostrarResultadosBehaviour(this, frame));
        System.out.println("[AgentUI] Listo. Esperando mensajes.");
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        if (frame != null) {
            SwingUtilities.invokeLater(frame::dispose);
        }
        System.out.println("[AgentUI] Finalizado: " + getLocalName());
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("monitor-fraude");
        sd.setType("interfaz-visualizacion");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[AgentUI] Registrado en el DF.");
        } catch (FIPAException e) {
            System.err.println("[AgentUI] Error al registrar en el DF: " + e.getMessage());
        }
    }
}
