package models;

public class Transaccion {

    private String txId;
    private String senderAccountId;
    private String receiverAccountId;
    private String txType;
    private double txAmount;
    private String timestamp;

    public Transaccion(String txId, String senderAccountId, String receiverAccountId,
                       String txType, double txAmount, String timestamp) {
        this.txId = txId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.txType = txType;
        this.txAmount = txAmount;
        this.timestamp = timestamp;
    }

    public String toMessageContent() {
        return txId + ";" +
                senderAccountId + ";" +
                receiverAccountId + ";" +
                txType + ";" +
                txAmount + ";" +
                timestamp;
    }
}