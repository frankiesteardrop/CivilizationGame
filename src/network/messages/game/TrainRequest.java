package network.messages.game;

import network.messages.Message;

public class TrainRequest extends Message {
    private final String unitType;

    public TrainRequest(String unitType) {
        super("TRAIN_REQUEST");
        this.unitType = unitType;
    }

    public String getUnitType() { return unitType; }
}