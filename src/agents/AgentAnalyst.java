package agents;

import jade.core.Agent;
import jade.core.AID;
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

// Importaciones de utilidades de Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class AgentAnalyst extends Agent {

    /**
     * Longitud mínima de un ciclo para considerarse sospechoso de blanqueo.
     * Ciclos de 2 cuentas (A↔B) son típicamente devoluciones / pagos cruzados,
     * no patrones de blanqueo.
     */
    private static final int MIN_CICLO_LONGITUD = 3;

    /**
     * Tolerancia para considerar dos importes "iguales" (comparación de double
     * con margen para errores de redondeo en la representación decimal).
     */
    private static final double EPSILON_IMPORTE = 0.01;

    /**
     * Firmas canónicas de ciclos ya emitidos a la UI. Evita que el mismo ciclo
     * se reenvíe en cada llamada de Tarjan al recibir el grafo acumulado.
     * Una "firma" colapsa todas las rotaciones del mismo ciclo dirigido
     * (p. ej. [A,B,C] y [B,C,A] producen la misma firma).
     */
    private final Set<String> ciclosYaEmitidos = new HashSet<>();

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
    private AID buscarUIenDF() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("interfaz-visualizacion");
        template.addServices(sd);
        try {
            DFAgentDescription[] resultados = DFService.search(this, template);
            if (resultados != null && resultados.length > 0) {
                return resultados[0].getName();
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
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
        // Buscamos el UI una sola vez antes del bucle
        AID uiAID = buscarUIenDF();
        if (uiAID == null) {
            System.err.println("[AgentAnalyst] AgentUI no encontrado en el DF. Alertas no enviadas.");
            return;
        }

        int nuevos = 0;
        int duplicados = 0;
        int descartadosPorTamano = 0;
        int descartadosPorImporte = 0;

        for (List<String> ciclo : ciclos) {

            // 0. Filtro estructural: longitud mínima del ciclo.
            if (ciclo.size() < MIN_CICLO_LONGITUD) {
                descartadosPorTamano++;
                continue;
            }

            // 1. Filtro de importes: todas las aristas del ciclo deben tener
            //    el mismo importe (patrón típico de blanqueo en cadena).
            if (!importesUniformes(ciclo, mapaImportes)) {
                descartadosPorImporte++;
                continue;
            }

            // 2. Dedupe: ¿ya hemos emitido este ciclo (en cualquiera de sus rotaciones)?
            String firma = firmaCanonica(ciclo);
            if (!ciclosYaEmitidos.add(firma)) {
                duplicados++;
                continue;
            }

            // 3. Preparamos la lista para construir el texto "A->B:5000; B->C:4000..."
            List<String> aristasFormateadas = new ArrayList<>();

            // 4. Recorremos el ciclo para enlazar cada cuenta con la siguiente
            for (int i = 0; i < ciclo.size(); i++) {
                String origen = ciclo.get(i);
                String destino = ciclo.get((i + 1) % ciclo.size());

                String claveArista = origen + "->" + destino;
                String importe = mapaImportes.getOrDefault(claveArista, "0");

                aristasFormateadas.add(origen + "->" + destino + ":" + importe);
            }

            // 5. Creamos el mensaje para ESTE ciclo
            ACLMessage alerta = new ACLMessage(ACLMessage.INFORM);
            alerta.addReceiver(uiAID);
            alerta.setConversationId("alerta-fraude");
            alerta.setContent(String.join(";", aristasFormateadas));
            send(alerta);
            nuevos++;
        }
        System.out.println("✉️ " + nuevos + " ciclos nuevos enviados al AgentUI ("
                + duplicados + " duplicados, "
                + descartadosPorTamano + " por tamaño <" + MIN_CICLO_LONGITUD + ", "
                + descartadosPorImporte + " por importes distintos).");
    }

    /**
     * Comprueba que todas las aristas del ciclo tienen el mismo importe
     * (con tolerancia {@link #EPSILON_IMPORTE} para errores de redondeo).
     * Devuelve false si algún importe no se puede parsear o si hay diferencias.
     */
    private boolean importesUniformes(List<String> ciclo,
                                      Map<String, String> mapaImportes) {
        Double referencia = null;
        for (int i = 0; i < ciclo.size(); i++) {
            String origen = ciclo.get(i);
            String destino = ciclo.get((i + 1) % ciclo.size());
            String importeStr = mapaImportes.get(origen + "->" + destino);

            if (importeStr == null) return false;

            double importe;
            try {
                importe = Double.parseDouble(importeStr);
            } catch (NumberFormatException e) {
                return false;
            }

            if (referencia == null) {
                referencia = importe;
            } else if (Math.abs(importe - referencia) > EPSILON_IMPORTE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calcula una firma canónica del ciclo invariante a rotaciones.
     * Rota la lista para que empiece por el vértice lex-mínimo, manteniendo
     * el sentido de recorrido (no invierte: ciclos en sentido contrario son
     * ciclos distintos en grafo dirigido).
     *
     * Ejemplo: [4412, 8740, 5419] y [8740, 5419, 4412] y [5419, 4412, 8740]
     * producen todos la firma "4412->5419->8740" (rotación que empieza
     * en el mínimo "4412").
     */
    private String firmaCanonica(List<String> ciclo) {
        if (ciclo == null || ciclo.isEmpty()) return "";

        // 1. Índice del vértice lex-mínimo
        int minIdx = 0;
        for (int i = 1; i < ciclo.size(); i++) {
            if (ciclo.get(i).compareTo(ciclo.get(minIdx)) < 0) {
                minIdx = i;
            }
        }

        // 2. Rotar empezando por ese índice, manteniendo el sentido
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ciclo.size(); i++) {
            if (i > 0) sb.append("->");
            sb.append(ciclo.get((minIdx + i) % ciclo.size()));
        }
        return sb.toString();
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