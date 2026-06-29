package model;

/**
 * یونیت مرزگشا — مسئول توسعه قلمرو امپراتوری.
 *
 * مکانیزم طبق داک:
 * - یک هکس انتخابی + ۶ همسایه‌اش را به مرز اضافه می‌کند
 * - هکس‌های هدف باید قبلاً توسط Explorer کشف شده باشند
 * - پس از استفاده، یونیت کاملاً حذف (Consume) می‌شود
 * - هزینه: ۲ AP قبل از مصرف
 */
public class BorderExpander extends Unit {

    /** هزینه AP برای اجرای عملیات مرزگشایی */
    private static final int EXPAND_AP_COST = 2;

    public BorderExpander(int q, int r) {
        // maxAP=4, foodConsumption=2, visionRadius=1
        super(q, r, 4, 2, 1);
    }

    /**
     * بررسی امکان اجرای مرزگشایی.
     * شرایط: یونیت زنده باشد، هکس جاری کشف‌شده باشد، AP کافی باشد.
     */
    public boolean canExpand(GameMap map) {
        if (!this.isAlive()) return false;
        if (this.getCurrentAP() < EXPAND_AP_COST) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());
        return currentHex != null && currentHex.isExplored();
    }

    /**
     * اجرای عملیات مرزگشایی.
     *
     * ترتیب دقیق:
     * ۱. اعتبارسنجی (canExpand)
     * ۲. کسر AP
     * ۳. گسترش مرز
     * ۴. آپدیت Fog of War
     * ۵. مصرف (Consume) یونیت
     *
     * @return true اگر عملیات موفق بود
     */
    public boolean expandBorder(GameMap map) {
        if (!canExpand(map)) return false;

        // کسر AP هزینه عملیات
        this.consumeAP(EXPAND_AP_COST);

        // اجرای گسترش مرز
        map.expandBorderAt(this.getQ(), this.getR());

        // آپدیت Fog of War پس از تغییر مرزها
        map.updateFogOfWar();

        // مصرف (Consume) کامل یونیت — طبق داک یک‌بار مصرف است
        this.kill();

        return true;
    }

    public static int getExpandApCost() { return EXPAND_AP_COST; }
}