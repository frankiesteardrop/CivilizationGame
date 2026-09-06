package controller;

import model.GameEventDispatcher;
import network.client.NetworkManager;

public class SaveLoadController {

    private final MainController mainController;

    public static class SaveMetadata {
        public boolean isEmpty    = true;
        public String  slotName   = "";
        public int     turnNumber = 0;
        public String  season     = "";
        public int     thLevel    = 1;
        public String  saveTime   = "";
        public String  saveVersion = "";
        public String  gameSummary = "";
    }

    public SaveLoadController(MainController mainController) {
        this.mainController = mainController;
    }

    public boolean saveGame(String slot) {
        NetworkManager nm = mainController.getNetworkManager();
        if (nm != null) {
            // Send save request as a generic network action (handled by GameStateManager later)
            nm.sendRequest(String.format("{\"type\":\"SAVE_GAME\", \"slot\":\"%s\"}", slot));
            GameEventDispatcher.fireNotification("⏳ Save request sent to server...");
            return true;
        }
        return false;
    }

    public void autosave() {
        saveGame("autosave");
    }

    public static SaveMetadata readSlotMetadata(String slot) {
        // Stateless fallback: UI directly relies on the server's authoritative state
        SaveMetadata meta = new SaveMetadata();
        meta.slotName = slot;
        meta.isEmpty = false;
        meta.season = "Server Storage";
        meta.saveTime = "Managed by DB";
        meta.gameSummary = "Multiplayer Data";
        return meta;
    }
}