package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

// Importaciones de JGraphT
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

// Importaciones extra para la memoria de importes
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class AgentAnalyst extends Agent {

    @Override
    protected void setup() {
        System.out.println("Agente Analista " + getAID().getName() + " inicializado.");

        // 1. Registrar el agente en el Directory Facilitator (DF)
        registrarServicioDF();

        // 2. Añadir el comportamiento principal para recibir el grafo del Constructor
        addBehaviour(new ReceptorGrafoBehaviour());
    }

    private void registrarServicioDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("analisis-fraude");
        sd.setName("JGraphT-Tarjan-Service");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Agente registrado en el DF correctamente.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    // Comportamiento cíclico que bloquea al agente hasta que llega el grafo cada 30 seg
    private class ReceptorGrafoBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            // Plantilla estricta basada en el contrato de tu compañero
            MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("grafo-listo")
            );

            // Filtro bloqueante: El agente duerme hasta que llega el mensaje
            ACLMessage mensaje = myAgent.blockingReceive(template);

            if (mensaje != null) {
                System.out.println("\n--- Nuevo grafo acumulado recibido del Constructor ---");
                procesarGrafoYDetectar(mensaje.getContent());
            }
        }
    }

    // Método para parsear el string, montar el grafo y detectar ciclos
    private void procesarGrafoYDetectar(String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            System.out.println("Aviso: El grafo recibido está vacío.");
            return;
        }

        // 1. Creamos un grafo nuevo y limpio en cada recepción
        Graph<String, DefaultEdge> grafoTransacciones = new DefaultDirectedGraph<>(DefaultEdge.class);

        // ¡NUEVO! Creamos un diccionario para memorizar los importes de cada arista
        Map<String, String> mapaImportes = new HashMap<>();

        // 2. Separamos el contenido línea por línea
        String[] lineas = contenido.split("\\r?\\n");
        int transaccionesValidas = 0;

        // 3. Procesamos cada línea del contrato
        for (String linea : lineas) {
            if (linea.trim().isEmpty()) continue;

            // Formato de tu compañero: TX_ID;SENDER;RECEIVER;TX_TYPE;AMOUNT;TIMESTAMP
            // Índices:                 0     1      2        3       4      5
            String[] columnas = linea.split(";");

            // Verificamos que al menos tiene hasta la columna AMOUNT (índice 4)
            if (columnas.length >= 5) {
                String sender = columnas[1].trim();
                String receiver = columnas[2].trim();
                String amount = columnas[4].trim();

                // Añadimos los vértices (cuentas). JGraphT ignora si ya existen.
                grafoTransacciones.addVertex(sender);
                grafoTransacciones.addVertex(receiver);

                // Añadimos la arista dirigida (SENDER -> RECEIVER)
                grafoTransacciones.addEdge(sender, receiver);
                transaccionesValidas++;

                // Guardamos el importe asociándolo a la dirección (ej. "CUENTA_A->CUENTA_B" = "5000.0")
                mapaImportes.put(sender + "->" + receiver, amount);
            }
        }

        System.out.println("Grafo construido con " + grafoTransacciones.vertexSet().size() +
                " nodos (cuentas) y " + transaccionesValidas + " aristas (transacciones).");

        // 4. Ejecutamos el algoritmo de Tarjan
        System.out.println("Iniciando algoritmo de Tarjan para buscar blanqueo de capitales...");
        TarjanSimpleCycles<String, DefaultEdge> tarjan = new TarjanSimpleCycles<>(grafoTransacciones);
        List<List<String>> ciclosDetectados = tarjan.findSimpleCycles();

        // 5. Mostramos los resultados y avisamos a la Interfaz
        if (ciclosDetectados.isEmpty()) {
            System.out.println("✅ Red segura: No se han detectado ciclos de fraude.");
        } else {
            System.out.println("🚨 ¡ALERTA! Se han detectado " + ciclosDetectados.size() + " ciclos de fraude.");
            for (int i = 0; i < ciclosDetectados.size(); i++) {
                System.out.println("   Ciclo " + (i+1) + ": " + ciclosDetectados.get(i));
            }
            // Llamamos al método que envía el mensaje al AgentUI pasándole también los importes
            enviarAlertasUI(ciclosDetectados, mapaImportes);
        }
    }

    // Método para enviar CADA ciclo detectado al Agente de Interfaz con importes y direcciones
    private void enviarAlertasUI(List<List<String>> ciclos, Map<String, String> mapaImportes) {
        for (List<String> ciclo : ciclos) {

            // 1. Preparamos la lista para construir el texto "A->B:5000; B->C:4000..."
            List<String> aristasFormateadas = new ArrayList<>();

            // 2. Recorremos el ciclo para enlazar cada cuenta con la siguiente
            for (int i = 0; i < ciclo.size(); i++) {
                String origen = ciclo.get(i);
                // El destino es el siguiente nodo. Si estamos en el último, el destino es el primero (cerrando el ciclo).
                String destino = ciclo.get((i + 1) % ciclo.size());

                // Reconstruimos la clave para buscar el importe en nuestra memoria
                String claveArista = origen + "->" + destino;
                String importe = mapaImportes.getOrDefault(claveArista, "0");

                // Añadimos el bloque formateado: "CUENTA_A->CUENTA_B:5000.0"
                aristasFormateadas.add(origen + "->" + destino + ":" + importe);
            }

            // 3. Creamos un nuevo mensaje para ESTE ciclo
            ACLMessage alerta = new ACLMessage(ACLMessage.INFORM);
            alerta.addReceiver(new jade.core.AID("AgentUI", jade.core.AID.ISLOCALNAME));
            alerta.setConversationId("alerta-fraude");

            // 4. Juntamos todas las aristas separadas por punto y coma (;)
            alerta.setContent(String.join(";", aristasFormateadas));

            // 5. Enviamos este mensaje ahora
            send(alerta);
        }
        System.out.println("✉️ Se han enviado " + ciclos.size() + " mensajes avanzados al f con importes y direcciones.");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Agente Analista " + getAID().getName() + " terminado.");
    }
}