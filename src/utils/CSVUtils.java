package utils;

import models.Transaccion;

public class CSVUtils {

    public static Transaccion parsearTransaccion(String linea) {
        try {
            String[] columnas = linea.split(",");

            if (columnas.length < 6) {
                return null;
            }

            String txId = columnas[0].trim();
            String senderAccountId = columnas[1].trim();
            String receiverAccountId = columnas[2].trim();
            String txType = columnas[3].trim();
            double txAmount = Double.parseDouble(columnas[4].trim());
            String timestamp = columnas[5].trim();

            if (txId.isEmpty() || senderAccountId.isEmpty() || receiverAccountId.isEmpty()) {
                return null;
            }

            if (txAmount <= 0) {
                return null;
            }

            return new Transaccion(txId, senderAccountId, receiverAccountId, txType, txAmount, timestamp);

        } catch (Exception e) {
            return null;
        }
    }
}