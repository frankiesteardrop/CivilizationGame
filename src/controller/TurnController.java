package controller;

import model.*;

public class TurnController {

    private final MainController mainController;
    private final GameMap        gameMap;

    public TurnController(MainController mainController, GameMap gameMap) {
        this.mainController = mainController;
        this.gameMap        = gameMap;
    }

    public boolean hasIdleUnits() {
        for (Unit u : gameMap.getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getCurrentAP() <= 0) continue;
            if (u instanceof Worker && ((Worker) u).isStationed()) continue;
            return true;
        }
        return false;
    }

    public void forceEndTurn() {
        // I3 (گام ۳): اطلاع به Pause Menu که Save در این لحظه مجاز نیست
        mainController.setProcessingTurn(true);
        try {
            executeEndTurnLogic();
        } finally {
            // تضمین reset شدن flag حتی در صورت exception
            mainController.setProcessingTurn(false);
        }
    }

    /**
     * منطق اصلی End Turn — جدا از flag management برای خوانایی بهتر.
     */
    private void executeEndTurnLogic() {
        int effectiveHappiness = mainController.getEconomyController()
                .getEffectiveHappiness(gameMap);

        // ─── ۱. تجدید AP یونیت‌ها و اعمال جریمه شورش (Rebellion) ───
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();

                if (effectiveHappiness <= -5) {
                    UnitType t = unit.getType();
                    if (t == UnitType.WORKER    || t == UnitType.SWORDSMAN
                            || t == UnitType.ARCHER    || t == UnitType.CAVALRY) {
                        unit.consumeAP(1);
                    }
                }
            }
        }

        // ─── ۲. کاهش تایمر توقف تولید سیل ──────────────────────────────────
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        // ─── ۳. پاکسازی ایمن واحدهای مرده ───────────────────────────────────
        gameMap.removeDeadUnits();

        // ─── ۴. N2: تشخیص تغییر فصل قبل از increment ────────────────────────
        Season seasonBefore = gameMap.getCurrentSeason();

        gameMap.incrementTurn();
        gameMap.updateFogOfWar();

        // ─── ۵. N2: Notification تغییر فصل ───────────────────────────────────
        Season seasonAfter = gameMap.getCurrentSeason();
        if (seasonBefore != seasonAfter) {
            fireSeasonChangeNotification(seasonAfter);
        }

        // ─── ۶. هوش مصنوعی خرس و بلایای طبیعی ─────────────────────────────
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // ─── ۷. اطلاع‌رسانی پایان نوبت و رفتار قبایل ────────────────────────
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
        mainController.getTribeController().processTribesTurn();

        // ─── ۸. ذخیره خودکار (Autosave) ───────────────────────────────────
        mainController.getSaveLoadController().autosave();
    }

    /**
     * N2: notification کامل و واضح هنگام تغییر فصل.
     *
     * طبق spec، تغییر فصل باید علاوه بر اثرات gameplay، از نظر بصری هم روی صفحه
     * مشخص باشد. این notification متنی اثرات جدید فصل را برای بازیکن توضیح می‌دهد
     * تا هیچ‌وقت بدون اطلاع با تغییر ناگهانی وضعیت بازی مواجه نشود.
     *
     * @param newSeason فصل جدیدی که شروع شده
     */
    private void fireSeasonChangeNotification(Season newSeason) {
        String message = switch (newSeason) {
            case SPRING ->
                    "🌸 Spring has arrived! (Turns 1-10 of cycle)\n"
                            + "✅ All Farms and Stables: +1 Food production per turn.";
            case SUMMER ->
                    "☀️ Summer begins! (Turns 11-20 of cycle)\n"
                            + "— No bonuses or penalties this season.";
            case AUTUMN ->
                    "🍂 Autumn is here! (Turns 21-30 of cycle)\n"
                            + "⚠ Water hex movement: +1 AP for all units.\n"
                            + "⚠ Flood risk is active — coastal and riverside areas vulnerable.";
            case WINTER ->
                    "❄️ Winter has come! (Turns 31-40 of cycle)\n"
                            + "⚠ All Farms: -1 Food production per turn.\n"
                            + "⚠ All land hex movement: +1 AP for ALL units (enemies included).";
        };
        GameEventDispatcher.fireNotification(message);
    }
}