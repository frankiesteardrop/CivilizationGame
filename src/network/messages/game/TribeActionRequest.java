package network.messages.game;

import model.ResourceType;
import network.messages.Message;

public class TribeActionRequest extends Message {
    private final int campQ;
    private final int campR;
    private final String action;
    private final ResourceType resourceType;
    private final int amount;
    private final ResourceType getResourceType;

    public TribeActionRequest(int campQ, int campR, String action,
                              ResourceType resourceType, int amount, ResourceType getResourceType) {
        super("TRIBE_ACTION");
        this.campQ = campQ;
        this.campR = campR;
        this.action = action;
        this.resourceType = resourceType;
        this.amount = amount;
        this.getResourceType = getResourceType;
    }

    public int getCampQ() { return campQ; }
    public int getCampR() { return campR; }
    public String getAction() { return action; }
    public ResourceType getResourceType() { return resourceType; }
    public int getAmount() { return amount; }
    public ResourceType getGetResourceType() { return getResourceType; }
}