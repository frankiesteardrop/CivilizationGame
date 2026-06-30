package controller;

import model.*;

/**
 * کنترلر مدیریت ساخت‌وساز.
 *
 * اصلاح گام ۶:
 * - canBuild اصلاح شد: بررسی‌های null-safe تر
 * - buildStructure: updateFogOfWar قبل از fireBuildingConstructed
 *   صدا زده می‌شود تا HUDPanel و GamePanel بلافاصله شعاع دید
 *   جدید ساختمان را ببینند
 * - کامنت‌های دقیق برای هر مرحله ساخت اضافه شد
 */
public class BuildController {

    private final GameMap gameMap;

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    // =========================================================
    // بررسی امکان ساخت
    // =========================================================

    /**
     * بررسی تمام شرایط لازم برای ساخت یک سازه.
     *
     * شرایط عمومی:
     * - هکس داخل مرز باشد
     * - هکس ساختمان نداشته باشد
     * - Builder زنده، شارژ داشته باشد
     * - Builder AP کافی داشته باشد
     * - منابع انبار کافی باشد
     *
     * شرایط اختصاصی هر نوع سازه:
     * - تناسب با نوع زمین و منبع هکس
     * - تکنولوژی لازم آنلاک شده باشد (برای معادن و Settlement)
     */
    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (hex == null || builder == null) return false;
        if (!builder.isAlive()) return false;

        // شرایط عمومی
        if (!hex.isInsideBorder())         return false;
        if (hex.getBuilding() != null)     return false;
        if (builder.getCharges() <= 0)     return false;
        if (builder.getCurrentAP() < type.getApCost()) return false;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        // بررسی تناسب با زمین و پیش‌نیازهای تکنولوژی
        boolean validTerrain = isValidTerrainForBuilding(type, hex, th);
        if (!validTerrain) return false;

        // بررسی موجودی انبار برای هزینه ساخت
        return inv.hasEnough(ResourceType.WOOD,  type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON,  type.getIronCost());
    }

    /**
     * بررسی تناسب نوع زمین با سازه درخواستی.
     * این منطق از canBuild جدا شده تا خوانایی و قابلیت تست را افزایش دهد.
     */
    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex, TownHall th) {
        switch (type) {

            case LUMBER_MILL:
                // فقط روی جنگل — جنگل همیشه WOOD دارد
                return hex.getTerrainType() == TerrainType.FOREST
                        && hex.hasResource(ResourceType.WOOD);

            case FARM:
                // روی سبزه‌زار دارای گندم/برنج
                return hex.getTerrainType() == TerrainType.MEADOW
                        && hex.hasResource(ResourceType.FOOD);

            case STABLE:
                // روی دشت دارای حیوانات اهلی
                return hex.getTerrainType() == TerrainType.PLAINS
                        && hex.hasResource(ResourceType.FOOD);

            case STONE_MINE:
                // روی کوهستان دارای سنگ + تکنولوژی آنلاک شده باشد
                return th.isStoneMineUnlocked()
                        && hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.STONE);

            case IRON_MINE:
                // روی کوهستان دارای آهن + تکنولوژی آنلاک شده باشد
                return th.isIronMineUnlocked()
                        && hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.IRON);

            case SETTLEMENT:
                // روی زمین‌های فاقد منبع — طبق داک
                // سبزه‌زار بدون گندم/برنج، دشت بدون حیوان،
                // کوهستان بدون معدن، جنگل بدون چوب (جنگل تخلیه‌شده)
                boolean hasAnyResource =
                        hex.hasResource(ResourceType.WOOD)  ||
                                hex.hasResource(ResourceType.STONE) ||
                                hex.hasResource(ResourceType.IRON)  ||
                                hex.hasResource(ResourceType.FOOD);
                return th.isSettlementUnlocked() && !hasAnyResource;

            default:
                return false;
        }
    }

    // =========================================================
    // اجرای ساخت
    // =========================================================

    /**
     * اجرای ساخت سازه روی هکس مشخص.
     *
     * ترتیب اجرا:
     * ۱. اعتبارسنجی نهایی با canBuild
     * ۲. کسر منابع از انبار
     * ۳. کسر AP از Builder
     * ۴. کاهش شارژ Builder (اگر به صفر رسید، Builder حذف می‌شود)
     * ۵. قرار دادن ساختمان روی هکس
     * ۶. آپدیت فوری Fog of War (شعاع دید ساختمان جدید اعمال می‌شود)
     * ۷. ارسال رویداد به لایه View برای رندر مجدد
     *
     * اصلاح گام ۶: updateFogOfWar قبل از fireBuildingConstructed
     * صدا زده می‌شود — این تضمین می‌کند که وقتی HUDPanel رویداد
     * را دریافت می‌کند و gamePanel.repaint() صدا می‌زند،
     * داده‌های Fog of War قبلاً آپدیت شده‌اند.
     */
    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        Inventory inv = gameMap.getTownHall().getInventory();

        // مرحله ۲: کسر منابع
        inv.consumeResource(ResourceType.WOOD,  type.getWoodCost());
        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
        inv.consumeResource(ResourceType.IRON,  type.getIronCost());

        // مرحله ۳: کسر AP
        builder.consumeAP(type.getApCost());

        // مرحله ۴: کاهش شارژ (و احتمالاً حذف Builder)
        builder.useCharge();

        // مرحله ۵: نصب ساختمان روی هکس
        Building newBuilding = createBuilding(type);
        hex.setBuilding(newBuilding);

        // مرحله ۶: آپدیت فوری Fog of War
        // (باید قبل از رویداد گرافیکی انجام شود)
        gameMap.updateFogOfWar();

        // مرحله ۷: اطلاع به لایه View
        GameEventDispatcher.fireBuildingConstructed(hex);
    }

    /**
     * Factory متد برای ساخت نمونه ساختمان بر اساس نوع آن.
     * جداسازی این منطق از buildStructure باعث می‌شود اضافه کردن
     * انواع ساختمان جدید در آینده فقط نیاز به تغییر این متد داشته باشد.
     */
    private Building createBuilding(BuildingType type) {
        switch (type) {
            case LUMBER_MILL: return new LumberMill();
            case STONE_MINE:  return new StoneMine();
            case IRON_MINE:   return new IronMine();
            case FARM:        return new Farm();
            case STABLE:      return new Stable();
            case SETTLEMENT:  return new Settlement();
            default:
                throw new IllegalArgumentException(
                        "Unknown building type: " + type);
        }
    }
}