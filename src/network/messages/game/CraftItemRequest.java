package network.messages.game;

import network.messages.Message;


public class CraftItemRequest extends Message {

    private final int apothecaryQ;

    private final int apothecaryR;


    private final String itemName;

    public CraftItemRequest(int apothecaryQ, int apothecaryR, String itemName) {
        super("CRAFT_ITEM");
        this.apothecaryQ = apothecaryQ;
        this.apothecaryR = apothecaryR;
        this.itemName    = itemName;
    }

    public int    getApothecaryQ() { return apothecaryQ; }
    public int    getApothecaryR() { return apothecaryR; }
    public String getItemName()    { return itemName; }
}