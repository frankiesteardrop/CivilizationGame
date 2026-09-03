package network.messages.game;

import network.messages.Message;

public class TradeResponseRequest extends Message {
    private final String tradeId;
    private final boolean accepted;

    public TradeResponseRequest(String tradeId, boolean accepted) {
        super("TRADE_RESPONSE");
        this.tradeId = tradeId;
        this.accepted = accepted;
    }

    public String getTradeId() { return tradeId; }
    public boolean isAccepted() { return accepted; }
}