package controller;

import model.BorderExpander;
import model.GameEventDispatcher;
import model.GameMap;
import model.Unit;
import model.Worker;

/**
 * کنترلر مدیریت نوبت‌ها.
 *
 * اصلاح گام ۶:
 * - hasIdleUnits دقیق‌تر شد: BorderExpander مصرف‌شده از لیست
 *   حذف نشده ممکن است isAlive=false داشته باشد — guard امنیتی اضافه شد
 * - Explorer که تمام AP دارد و هنوز کاری نکرده idle است
 * - Worker که AP دارد ولی stationed نیست idle است
 * - Builder که AP دارد و می‌تواند بسازد idle است
 */
public class TurnController {

    private final GameMap gameMap;

    public TurnController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    // =========================================================
    // بررسی وجود یونیت‌های Idle
    // =========================================================

    /**
     * بررسی وجود یونیت‌های Idle.
     *
     * تعریف Idle: یونیتی که:
     * ۱. زنده است (isAlive = true)
     * ۲. AP دارد (currentAP > 0)
     * ۳. مشغول کار نیست (Worker stationed نیست)
     *
     * اصلاح گام ۶:
     * - بررسی isAlive تقویت شد (guard در برابر یونیت‌های مصرف‌شده‌ای
     *   که هنوز از لیست پاک نشده‌اند — مثل BorderExpander بعد از expand)
     * - منطق واضح‌تر و مستندتر شد
     */
    public boolean hasIdleUnits() {
        for (Unit u : gameMap.getUnits()) {
            // صرفاً یونیت‌های زنده بررسی می‌شوند
            if (!u.isAlive()) continue;

            // یونیت‌های بدون AP قادر به انجام کاری نیستند — idle نیستند
            if (u.getCurrentAP() <= 0) continue;

            // Worker مستقر در ساختمان مشغول تولید است — idle نیست
            if (u instanceof Worker && ((Worker) u).isStationed()) continue;

            // BorderExpander که هنوز در لیست است ولی مصرف شده
            // (حالت دفاعی — معمولاً removeIf در nextTurn این را پاک می‌کند)
            if (u instanceof BorderExpander && !u.isAlive()) continue;

            // این یونیت زنده، AP دارد و مشغول کاری نیست → idle است
            return true;
        }
        return false;
    }

    // =========================================================
    // اجرای پایان نوبت
    // =========================================================

    /**
     * اجرای قطعی پایان نوبت.
     *
     * ترتیب اجرا:
     * ۱. چرخه بازی پیش می‌رود (GameMap.nextTurn)
     * ۲. رویداد TurnEnded به همه listener‌ها ارسال می‌شود
     */
    public void forceEndTurn() {
        gameMap.nextTurn();
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}