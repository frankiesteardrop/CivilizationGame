package network.messages.game;

import model.TradeOffer;
import network.messages.Message;

import java.util.List;


public class TradeInboxBroadcast extends Message {

    private final List<TradeOffer> pendingOffers;

    public TradeInboxBroadcast(List<TradeOffer> pendingOffers) {
        super("TRADE_INBOX_UPDATE");
        this.pendingOffers = pendingOffers;
    }

    public List<TradeOffer> getPendingOffers() { return pendingOffers; }
}