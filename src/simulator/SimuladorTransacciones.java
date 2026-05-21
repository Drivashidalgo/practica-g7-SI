package simulator;

import java.io.*;

public class SimuladorTransacciones {

    public static void main(String[] args) {
        String archivoOriginal = "data/transactions.csv";
        String archivoLive = "data/transactions_live.csv";
        int intervaloMs = 10;

        try (
                BufferedReader reader = new BufferedReader(new FileReader(archivoOriginal));
                BufferedWriter writer = new BufferedWriter(new FileWriter(archivoLive))
        ) {
            String cabecera = reader.readLine();

            if (cabecera == null) {
                System.out.println("El archivo original está vacío.");
                return;
            }

            writer.write(cabecera);
            writer.newLine();
            writer.flush();

            System.out.println("Simulador iniciado.");
            System.out.println("Generando transacciones en: " + archivoLive);

            String linea;

            while ((linea = reader.readLine()) != null) {
                String[] cols = linea.split(",");
                // cols[5] es TIMESTAMP, saltamos las <= 100
                if (cols.length > 5) {
                    try {
                        int ts = Integer.parseInt(cols[5].trim());
                        if (ts <= 100) continue; // ignorar histórico
                    } catch (NumberFormatException ignored) {}
                }

                writer.write(linea);
                writer.newLine();
                writer.flush();
                System.out.println("Nueva transacción generada: " + linea);
                Thread.sleep(intervaloMs);
            }

            System.out.println("Simulación terminada.");

        } catch (IOException e) {
            System.err.println("Error con los archivos: " + e.getMessage());

        } catch (InterruptedException e) {
            System.err.println("Simulación interrumpida.");
        }
    }
}