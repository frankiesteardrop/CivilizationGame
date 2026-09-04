package model;

import java.util.UUID;

/**
 * Represents a pending player-to-player trade offer.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Player A sends {@link network.messages.game.TradeOfferRequest}.</li>
 *   <li>Server validates A's resources, locks them, creates this object,
 *       and places it in Player B's trade inbox.</li>
 *   <li>Player B accepts → resources are transferred, locked resources released.</li>
 *   <li>Player B rejects OR Player A cancels → locked resources are released.</li>
 * </ol>
 *
 * <p>Both {@code id} and {@code createdAt} satisfy the spec requirement for
 * all server-managed entities to have a unique identifier and a creation timestamp.
 */
public class TradeOffer {

    /** Unique ID used to reference this specific offer in accept/reject/cancel requests. */
    private final String id;

    /** ID of the player who created this offer. */
    private final String offererId;

    /** Username of the offerer — stored for display purposes (avoids server lookup). */
    private final String offererName;

    /** ID of the player this offer is directed at. */
    private final String targetId;

    /** The resource type Player A is offering. */
    private final ResourceType offerType;

    /** The amount of {@link #offerType} Player A is offering. */
    private final int offerAmount;

    /** The resource type Player A is requesting in return. */
    private final ResourceType requestType;

    /** The amount of {@link #requestType} Player A is requesting. */
    private final int requestAmount;

    /** Unix epoch milliseconds when this offer was created. */
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