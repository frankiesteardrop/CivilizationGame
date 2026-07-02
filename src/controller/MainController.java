package controller;

import model.GameMap;

/**
 * الگوی طراحی Facade: این کلاس دسترسی متمرکز به تمامی کنترلرهای تخصصی و زیرسیستم‌ها را برای لایه View فراهم می‌کند.
 */
public class MainController {
    private final GameMap gameMap;
    private final TurnController turnController;
    private final UnitController unitController;
    private final BuildController buildController;
    private final UpgradeController upgradeController;

    public MainController(GameMap gameMap) {
        this.gameMap = gameMap;
        this.turnController = new TurnController(gameMap);
        this.unitController = new UnitController();
        this.buildController = new BuildController(gameMap);
        this.upgradeController = new UpgradeController(gameMap);
    }

    public GameMap getGameMap() { return gameMap; }
    public TurnController getTurnController() { return turnController; }
    public UnitController getUnitController() { return unitController; }
    public BuildController getBuildController() { return buildController; }
    public UpgradeController getUpgradeController() { return upgradeController; }

    // =========================================================
    // [گام حل باگ ۲۳ - رعایت دقیق الگوی Facade]:
    // تفویض اختیار (Delegation) مدیریت سیستم صوتی به لایه کنترلر
    // تا لایه View هرگز مستقیماً با زیرسیستم AudioManager درگیر نشود.
    // =========================================================
    public static void setMusicVolume(int volumePercent) {
        AudioManager.setVolume(volumePercent);
    }

    public static int getMusicVolume() {
        return AudioManager.getCurrentVolume();
    }

    public static void playMusic(String resourcePath) {
        AudioManager.playMusic(resourcePath);
    }

    public static void stopMusic() {
        AudioManager.stopMusic();
    }
}