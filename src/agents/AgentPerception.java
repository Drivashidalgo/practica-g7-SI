package agents;

import behaviours.LeerCSVLiveBehaviour;
import jade.core.Agent;

public class AgentePercepcion extends Agent {

    @Override
    protected void setup() {
        System.out.println("Agente de Percepción iniciado: " + getLocalName());

        String archivoLive = "data/transactions_live.csv";
        String agenteConstructor = "constructor";
        long intervaloRevision = 1000;

        addBehaviour(new LeerCSVLiveBehaviour(this, intervaloRevision, archivoLive, agenteConstructor));
    }
}