package network.messages.game;

import network.messages.Message;

/**
 * Sent by the client when the player orders an Apothecary to craft an item.
 * The server validates ownership, queue availability, and resources before accepting.
 */
public class CraftItemRequest extends Message {

    /** Axial Q coordinate of the Apothecary building. */
    private final int apothecaryQ;

    /** Axial R coordinate of the Apothecary building. */
    private final int apothecaryR;

    /**
     * Name of the item to craft.
     * Must match one of {@link model.Apothecary.ItemType#name()} values:
     * "TELEPORT", "MOBILITY", or "COMBAT".
     */
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