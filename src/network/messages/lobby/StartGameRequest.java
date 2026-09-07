package network.messages.lobby;
import network.messages.Message;

public class StartGameRequest extends Message {
    public StartGameRequest() {
        super("START_GAME");
    }
}