package network.client;

/**
 * Interface for dispatching server messages to the appropriate client-side controller.
 * Implement this in a coordinator class that delegates to LobbyController or GameController.
 * All calls to onMessage are guaranteed to execute on the EDT.
 */
public interface ServerMessageHandler {

    /**
     * Called (on EDT) whenever a complete JSON message arrives from the server.
     *
     * @param messageType the value of the "type" field in the JSON
     * @param rawJson     the complete raw JSON string for deserialization
     */
    void onMessage(String messageType, String rawJson);
}