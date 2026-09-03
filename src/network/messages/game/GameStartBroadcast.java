package network.messages.game;

import network.messages.Message;

/**
 * Broadcast from server to all clients when the host clicks "Start Game"
 * and all players are ready.
 *
 * <p>Upon receiving this message, each client must:
 * <ol>
 *   <li>Close the LobbyPanel UI.</li>
 *   <li>Open the main GamePanel UI.</li>
 * </ol>
 * The initial game state arrives immediately after as a {@link GameStateBroadcast}.
 */
public class GameStartBroadcast extends Message {

    public GameStartBroadcast() {
        super("GAME_START_BROADCAST");
    }
}