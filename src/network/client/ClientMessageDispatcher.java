package network.client;

import com.google.gson.Gson;
import controller.MainController;
import controller.LobbyController;
import network.messages.game.*;
import network.messages.lobby.ChatMessageBroadcast;
import network.messages.lobby.LobbyUpdateBroadcast;
import network.messages.lobby.PlayerIdAssignedMessage;
import view.HUDPanel;
import view.GamePanel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientMessageDispatcher implements ServerMessageHandler {

    private final LobbyController lobbyController;
    private final Gson gson = new Gson();

    private String myPlayerId = null;
    private HUDPanel hudPanel = null;

    private MainController mainController = null;
    private GamePanel gamePanel = null;

    private Runnable                           onGameStarted;
    private Consumer<GameStateBroadcast>       onGameStateUpdate;
    private Runnable                           onDisconnected;
    private Consumer<TradeInboxBroadcast>      onTradeInboxUpdate;
    private Consumer<DiplomacyBroadcast>       onDiplomacyEvent;

    private java.util.function.Consumer<String> onMyPlayerIdReceived;

    public ClientMessageDispatcher(LobbyController lobbyController) {
        this.lobbyController = lobbyController;
    }

    public void setOnGameStarted(Runnable cb)                        { onGameStarted = cb; }
    public void setOnGameStateUpdate(Consumer<GameStateBroadcast> cb){ onGameStateUpdate = cb; }
    public void setOnDisconnected(Runnable cb)                       { onDisconnected = cb; }
    public void setOnTradeInboxUpdate(Consumer<TradeInboxBroadcast> cb){ onTradeInboxUpdate = cb; }
    public void setOnDiplomacyEvent(Consumer<DiplomacyBroadcast> cb) { onDiplomacyEvent = cb; }
    public void setMyPlayerId(String id)                             { myPlayerId = id; }
    public void setHudPanel(HUDPanel panel)                          { hudPanel = panel; }

    public void setMainController(MainController mc)                 { this.mainController = mc; }
    public void setGamePanel(GamePanel gp)                           { this.gamePanel = gp; }

    public void setOnMyPlayerIdReceived(java.util.function.Consumer<String> cb) { this.onMyPlayerIdReceived = cb; }

    @Override
    public void onMessage(String messageType, String rawJson) {
        switch (messageType) {

            // دریافت شناسه یکتا از سرور
            case "PLAYER_ID_ASSIGNED" -> {
                PlayerIdAssignedMessage msg = gson.fromJson(rawJson, PlayerIdAssignedMessage.class);
                myPlayerId = msg.getAssignedId();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (mainController != null) {
                        mainController.setMyPlayerId(myPlayerId);
                    }
                    if (onMyPlayerIdReceived != null) {
                        onMyPlayerIdReceived.accept(myPlayerId);
                        onMyPlayerIdReceived = null;
                    }
                });
            }

            case "LOBBY_UPDATE" -> {
                LobbyUpdateBroadcast update = gson.fromJson(rawJson, LobbyUpdateBroadcast.class);
                lobbyController.handleLobbyUpdate(update);
            }

            case "CHAT_MESSAGE" -> {
                ChatMessageBroadcast chat = gson.fromJson(rawJson, ChatMessageBroadcast.class);
                String formatted = String.format("[%s] %s: %s\n",
                        chat.getTimestamp(), chat.getSenderName(), chat.getText());
                lobbyController.handleChatMessage(chat);
                if (hudPanel != null) {
                    hudPanel.appendGameChatMessage(formatted);
                }
            }

            case "GAME_START_BROADCAST" -> {
                System.out.println("[Client] Server started the game — switching to game view.");
                if (onGameStarted != null) onGameStarted.run();
            }

            case "GAME_STATE_UPDATE" -> {
                GameStateBroadcast broadcast = gson.fromJson(rawJson, GameStateBroadcast.class);
                if (onGameStateUpdate != null) onGameStateUpdate.accept(broadcast);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    String json = broadcast.getFilteredMapJson();
                    if (json != null && !json.isBlank() && mainController != null) {
                        mainController.applyServerState(json);
                        if (gamePanel != null) {
                            gamePanel.repaint();
                        }
                    }

                    if (hudPanel != null) {
                        String activeId   = broadcast.getActivePlayerId();
                        boolean isMyTurn  = activeId != null && activeId.equals(myPlayerId);
                        hudPanel.setActiveTurnInfo(activeId, isMyTurn);

                        if (mainController != null) {
                            mainController.setMyTurn(isMyTurn);
                        }

                        if (isMyTurn) {
                            hudPanel.onOurTurnStarted();
                        }
                    }
                });
            }

            case "WAT_REPORT" -> {
                WatReportBroadcast report = gson.fromJson(rawJson, WatReportBroadcast.class);
                if (report.getReports() != null) {
                    for (model.WatReport wr : report.getReports()) {
                        model.GameEventDispatcher.fireNotification(wr.toDisplayText());
                    }
                }
            }

            case "TRADE_INBOX_UPDATE" -> {
                TradeInboxBroadcast inbox = gson.fromJson(rawJson, TradeInboxBroadcast.class);
                if (onTradeInboxUpdate != null) {
                    onTradeInboxUpdate.accept(inbox);
                }
                if (hudPanel != null) {
                    hudPanel.updateTradeInbox(inbox.getPendingOffers());
                }
            }

            case "DIPLOMACY_EVENT" -> {
                DiplomacyBroadcast event = gson.fromJson(rawJson, DiplomacyBroadcast.class);
                model.GameEventDispatcher.fireNotification(event.getAnnouncementText());
                if (onDiplomacyEvent != null) onDiplomacyEvent.accept(event);
                if (hudPanel != null) {
                    updateHudDiplomacy(event);
                }
            }

            case "GAME_NOTIFICATION" -> {
                GameNotificationMessage notif = gson.fromJson(rawJson, GameNotificationMessage.class);
                model.GameEventDispatcher.fireNotification(notif.getText());
            }

            case "ERROR_RESPONSE" -> {
                ErrorResponse err = gson.fromJson(rawJson, ErrorResponse.class);
                System.err.println("[Client] Server error: " + err.getErrorMessage());
                model.GameEventDispatcher.fireNotification("⚠️ " + err.getErrorMessage());
            }

            case "DISCONNECTED" -> {
                System.out.println("[Client] Disconnected from server.");
                if (onDisconnected != null) onDisconnected.run();
            }

            default ->
                    System.out.println("[Client] Unhandled message type: " + messageType);
        }
    }

    private void updateHudDiplomacy(DiplomacyBroadcast event) {
        if (hudPanel == null || myPlayerId == null) return;

        String initiatorId   = event.getInitiatorId();
        String initiatorName = event.getInitiatorName();
        String targetId      = event.getTargetId();
        String targetName    = event.getTargetName();

        Map<String, String[]> statusMap = new LinkedHashMap<>();

        String otherPlayerId;
        String otherPlayerName;

        if (myPlayerId.equals(initiatorId)) {
            otherPlayerId   = targetId;
            otherPlayerName = targetName;
        } else if (myPlayerId.equals(targetId)) {
            otherPlayerId   = initiatorId;
            otherPlayerName = initiatorName;
        } else {
            otherPlayerId   = initiatorId;
            otherPlayerName = initiatorName;
        }

        String newStatus = switch (event.getEventType()) {
            case "WAR_DECLARED"    -> "Enemy";
            case "ALLIANCE_FORMED" -> "Allied";
            case "ALLIANCE_BROKEN" -> "Neutral";
            default                -> "Neutral";
        };

        statusMap.put(otherPlayerId, new String[]{ otherPlayerName, newStatus });
        hudPanel.updateDiplomacyStatus(statusMap);
    }
}