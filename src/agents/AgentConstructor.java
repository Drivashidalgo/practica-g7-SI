package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class AgentConstructor extends Agent {

    @Override
    protected void setup() {
        System.out.println("Agente Constructor iniciado: " + getLocalName());

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage mensaje = receive();

                if (mensaje != null) {
                    System.out.println("Constructor ha recibido: " + mensaje.getContent());
                } else {
                    block();
                }
            }
        });
    }
}