package model;

/**
 * انواع قبایل بی‌طرف طبق spec فاز دوم.
 * HP کمپ هر قبیله:
 *   FARMER   → 40 HP
 *   WARRIOR  → 70 HP
 *   MOUNTAIN → 50 HP
 *   COMMERCIAL (قبیله تجاری) → 50 HP
 *   COASTAL (قبیله ساحلی)    → 50 HP
 *
 * اصلاح F-37: MERCHANT → COMMERCIAL، NOMAD → COASTAL
 */
public enum TribeType {

    FARMER(40,   "Farmer",     "کشاورز"),
    WARRIOR(70,  "Warrior",    "جنگجو"),
    MOUNTAIN(50, "Mountain",   "کوهستانی"),
    COMMERCIAL(50, "Commercial", "تجاری"),    // اصلاح: قبلاً MERCHANT
    COASTAL(50,  "Coastal",    "ساحلی");      // اصلاح: قبلاً NOMAD

    private final int    maxHp;
    private final String displayName;
    private final String persianName;

    TribeType(int maxHp, String displayName, String persianName) {
        this.maxHp       = maxHp;
        this.displayName = displayName;
        this.persianName = persianName;
    }

    public int    getMaxHp()       { return maxHp; }
    public String getDisplayName() { return displayName; }
    public String getPersianName() { return persianName; }
}