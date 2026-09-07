package network.messages.game;

import network.messages.Message;

public class ItemUseRequest extends Message {
    private final String unitId;
    private final int targetQ;
    private final int targetR;
    private final String itemName;
    private final int destQ;
    private final int destR;

    public ItemUseRequest(String unitId, int targetQ, int targetR, String itemName, int destQ, int destR) {
        super("ITEM_USE");
        this.unitId = unitId;
        this.targetQ = targetQ;
        this.targetR = targetR;
        this.itemName = itemName;
        this.destQ = destQ;
        this.destR = destR;
    }

    public String getUnitId() { return unitId; }
    public int getTargetQ() { return targetQ; }
    public int getTargetR() { return targetR; }
    public String getItemName() { return itemName; }
    public int getDestQ() { return destQ; }
    public int getDestR() { return destR; }
}