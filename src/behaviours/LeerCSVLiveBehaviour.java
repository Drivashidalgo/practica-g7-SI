package behaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import models.Transaccion;
import utils.CSVUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LeerCSVLiveBehaviour extends TickerBehaviour {

    private String archivoLive;
    private String agenteDestino;
    private int lineasLeidas = 0;

    public LeerCSVLiveBehaviour(Agent agente, long intervaloMs, String archivoLive, String agenteDestino) {
        super(agente, intervaloMs);
        this.archivoLive = archivoLive;
        this.agenteDestino = agenteDestino;
    }

    @Override
    protected void onTick() {
        try (BufferedReader reader = new BufferedReader(new FileReader(archivoLive))) {
            String linea;
            int numeroLinea = 0;

            while ((linea = reader.readLine()) != null) {
                if (numeroLinea > lineasLeidas) {
                    procesarLinea(linea);
                }

                numeroLinea++;
            }

            lineasLeidas = numeroLinea - 1;

        } catch (IOException e) {
            System.err.println("Esperando archivo live: " + archivoLive);
        }
    }

    private void procesarLinea(String linea) {
        Transaccion transaccion = CSVUtils.parsearTransaccion(linea);

        if (transaccion == null) {
            System.err.println("Transacción inválida ignorada: " + linea);
            return;
        }

        enviarTransaccion(transaccion);
    }

    private void enviarTransaccion(Transaccion transaccion) {
        ACLMessage mensaje = new ACLMessage(ACLMessage.INFORM);

        mensaje.addReceiver(new AID(agenteDestino, AID.ISLOCALNAME));
        mensaje.setConversationId("transaccion-bancaria");
        mensaje.setContent(transaccion.toMessageContent());

        myAgent.send(mensaje);

        System.out.println("Transacción enviada al constructor: " + transaccion.toMessageContent());
    }
}