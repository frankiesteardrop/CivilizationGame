package network.client;


public interface ServerMessageHandler {


    void onMessage(String messageType, String rawJson);
}