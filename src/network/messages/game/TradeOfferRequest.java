package network.messages.game;

import network.messages.Message;
import model.ResourceType;

public class TradeOfferRequest extends Message {
    private final String targetPlayerId;
    private final ResourceType offerType;
    private final int offerAmount;
    private final ResourceType requestType;
    private final int requestAmount;

    public TradeOfferRequest(String targetPlayerId, ResourceType offerType, int offerAmount, ResourceType requestType, int requestAmount) {
        super("TRADE_OFFER");
        this.targetPlayerId = targetPlayerId;
        this.offerType = offerType;
        this.offerAmount = offerAmount;
        this.requestType = requestType;
        this.requestAmount = requestAmount;
    }

    public String getTargetPlayerId() { return targetPlayerId; }
    public ResourceType getOfferType() { return offerType; }
    public int getOfferAmount() { return offerAmount; }
    public ResourceType getRequestType() { return requestType; }
    public int getRequestAmount() { return requestAmount; }
}