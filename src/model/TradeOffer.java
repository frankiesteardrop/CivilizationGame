package model;

import java.util.UUID;

public class TradeOffer {

    private final String id;

    private final String offererId;

    private final String offererName;

    private final String targetId;

    private final ResourceType offerType;

    private final int offerAmount;

    private final ResourceType requestType;

    private final int requestAmount;

    private final long createdAt;

    public TradeOffer(String offererId, String offererName, String targetId,
                      ResourceType offerType, int offerAmount,
                      ResourceType requestType, int requestAmount) {
        this.id            = UUID.randomUUID().toString();
        this.offererId     = offererId;
        this.offererName   = offererName;
        this.targetId      = targetId;
        this.offerType     = offerType;
        this.offerAmount   = offerAmount;
        this.requestType   = requestType;
        this.requestAmount = requestAmount;
        this.createdAt     = System.currentTimeMillis();
    }

    public String       getId()            { return id; }
    public String       getOffererId()     { return offererId; }
    public String       getOffererName()   { return offererName; }
    public String       getTargetId()      { return targetId; }
    public ResourceType getOfferType()     { return offerType; }
    public int          getOfferAmount()   { return offerAmount; }
    public ResourceType getRequestType()   { return requestType; }
    public int          getRequestAmount() { return requestAmount; }
    public long         getCreatedAt()     { return createdAt; }
}