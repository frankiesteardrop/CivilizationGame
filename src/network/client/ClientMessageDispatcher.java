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
                        String activeName = broadcast.getActivePlayerName();
                        boolean isMyTurn  = activeId != null && activeId.equals(myPlayerId);

                        hudPanel.setActiveTurnInfo(activeName, isMyTurn);

                        if (mainController != null) {
                            mainController.setMyTurn(isMyTurn);
                        }
                        if (isMyTurn) {
                            hudPanel.onOurTurnStarted();
                        }

                        Map<String, String[]> diplomacyMap = broadcast.getDiplomacyStatuses();
                        if (diplomacyMap != null) {
                            hudPanel.updateDiplomacyStatus(diplomacyMap);
                        }
                    }
                });
            }

            case "WAR_REPORT" -> {
                WarReportBroadcast report = gson.fromJson(rawJson, WarReportBroadcast.class);
                if (report.getReports() != null) {
                    for (model.WarReport wr : report.getReports()) {
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

                if ("ALLIANCE_REQUESTED".equals(event.getEventType()) && myPlayerId.equals(event.getTargetId())) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        int choice = javax.swing.JOptionPane.showConfirmDialog(
                                null,
                                event.getInitiatorName() + " has requested a permanent alliance with you.\nDo you accept?",
                                "Alliance Request 🤝",
                                javax.swing.JOptionPane.YES_NO_OPTION,
                                javax.swing.JOptionPane.QUESTION_MESSAGE
                        );
                        boolean accepted = (choice == javax.swing.JOptionPane.YES_OPTION);
                        if (mainController != null && mainController.getNetworkManager() != null) {
                            mainController.getNetworkManager().sendRequest(gson.toJson(new AllianceResponseRequest(event.getInitiatorId(), accepted)));
                        }
                    });
                    return;
                }

                if (!"GAME_START".equals(event.getEventType())) {
                    model.GameEventDispatcher.fireNotification(event.getAnnouncementText());
                }

                if (onDiplomacyEvent != null) onDiplomacyEvent.accept(event);
            }

            case "GAME_NOTIFICATION" -> {
                GameNotificationMessage notif = gson.fromJson(rawJson, GameNotificationMessage.class);
                model.GameEventDispatcher.fireNotification(notif.getText());
            }

            case "ERROR_RESPONSE" -> {
                ErrorResponse err = gson.fromJson(rawJson, ErrorResponse.class);
                System.err.println("[Client] Server error: " + err.getErrorMessage());
                model.GameEventDispatcher.fireNotification(err.getErrorMessage());
            }

            case "DISCONNECTED" -> {
                System.out.println("[Client] Disconnected from server.");
                if (onDisconnected != null) onDisconnected.run();
            }

            default ->
                    System.out.println("[Client] Unhandled message type: " + messageType);
        }
    }
}