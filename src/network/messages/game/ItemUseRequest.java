package network.messages.game;

import network.messages.Message;

public class ItemUseRequest extends Message {
    private final int targetQ;
    private final int targetR;
    private final String itemName; // "TELEPORT", "MOBILITY", "COMBAT"
    private final int destQ; // فقط برای تله‌پورت
    private final int destR; // فقط برای تله‌پورت

    public ItemUseRequest(int targetQ, int targetR, String itemName, int destQ, int destR) {
        super("ITEM_USE");
        this.targetQ = targetQ;
        this.targetR = targetR;
        this.itemName = itemName;
        this.destQ = destQ;
        this.destR = destR;
    }

    public int getTargetQ() { return targetQ; }
    public int getTargetR() { return targetR; }
    public String getItemName() { return itemName; }
    public int getDestQ() { return destQ; }
    public int getDestR() { return destR; }
}