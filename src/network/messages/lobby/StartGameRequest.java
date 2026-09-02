package network.messages.lobby;
import network.messages.Message;

// درخواست شروع بازی (فقط توسط هاست)
public class StartGameRequest extends Message {
    public StartGameRequest() {
        super("START_GAME");
    }
}