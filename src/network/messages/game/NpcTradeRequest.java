package network.messages.game;

import model.ResourceType;
import network.messages.Message;

public class NpcTradeRequest extends Message {
    private final int hexQ;
    private final int hexR;
    private final String buildingType;
    private final ResourceType giveType;
    private final int amountToGive;
    private final ResourceType getType;

    public NpcTradeRequest(int hexQ, int hexR, String buildingType,
                           ResourceType giveType, int amountToGive, ResourceType getType) {
        super("NPC_TRADE");
        this.hexQ = hexQ;
        this.hexR = hexR;
        this.buildingType = buildingType;
        this.giveType = giveType;
        this.amountToGive = amountToGive;
        this.getType = getType;
    }

    public int getHexQ() { return hexQ; }
    public int getHexR() { return hexR; }
    public String getBuildingType() { return buildingType; }
    public ResourceType getGiveType() { return giveType; }
    public int getAmountToGive() { return amountToGive; }
    public ResourceType getGetType() { return getType; }
}