package network.messages.game;

import network.messages.Message;

public class CancelTradeRequest extends Message {
    private final String tradeId;

    public CancelTradeRequest(String tradeId) {
        super("TRADE_CANCEL");
        this.tradeId = tradeId;
    }

    public String getTradeId() { return tradeId; }
}