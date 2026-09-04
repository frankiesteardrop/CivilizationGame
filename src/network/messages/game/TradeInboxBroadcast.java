package network.messages.game;

import model.TradeOffer;
import network.messages.Message;

import java.util.List;

/**
 * Sent by the server to a specific client when their trade inbox changes
 * (a new offer arrives, or an existing offer is removed).
 *
 * <p>The client should display the pending offers in a Trade Inbox UI,
 * allowing the player to accept or reject each one.
 */
public class TradeInboxBroadcast extends Message {

    /** All pending trade offers directed at this client. May be empty. */
    private final List<TradeOffer> pendingOffers;

    public TradeInboxBroadcast(List<TradeOffer> pendingOffers) {
        super("TRADE_INBOX_UPDATE");
        this.pendingOffers = pendingOffers;
    }

    public List<TradeOffer> getPendingOffers() { return pendingOffers; }
}