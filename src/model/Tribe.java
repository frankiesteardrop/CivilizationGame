package model;

/**
 * نگهداری وضعیت رابطه قبیله با بازیکن.
 *
 * بازه‌های رابطه طبق spec فاز دوم (اصلاح F-03):
 *   -100 تا -50 → Enemy (دشمن)
 *    -49 تا -20 → Displeased (ناراضی)
 *    -19 تا +19 → Neutral (خنثی)
 *    +20 تا +69 → Friendly (دوستانه)
 *    +70 تا +100 → Allied (متحد)
 *
 * قوانین تعامل:
 *   Enemy:     فقط درخواست صلح مجاز
 *   Displeased: هیچ Trade/Mission/Alliance
 *   Neutral:   فقط هدیه
 *   Friendly:  Trade + Mission
 *   Allied:    همه + permanent bonus
 */
public class Tribe {

    private final TribeType type;
    private int     relationship; // -100 تا +100
    private boolean isAllied;

    public Tribe(TribeType type) {
        this.type         = type;
        this.relationship = 0; // Neutral از ابتدا
        this.isAllied     = false;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public TribeType getType()         { return type; }
    public int  getRelationship()      { return relationship; }
    public boolean isAllied()          { return isAllied; }
    public void setAllied(boolean a)   { this.isAllied = a; }

    /**
     * تغییر مقدار رابطه با اعمال کلمپ -100 تا +100.
     */
    public void addRelationship(int amount) {
        this.relationship = Math.max(-100, Math.min(100, this.relationship + amount));
        // اگر رابطه از allied threshold پایین آمد، اتحاد لغو می‌شود
        if (this.relationship < 70 && this.isAllied) {
            this.isAllied = false;
        }
    }

    /**
     * وضعیت رابطه بر اساس بازه‌های دقیق spec (اصلاح F-03).
     * توجه: Allied از طریق isAllied flag یا رابطه ≥70 تشخیص داده می‌شود.
     */
    public String getStatus() {
        if (relationship <= -50) return "Enemy";       // -100 تا -50
        if (relationship <= -20) return "Displeased";  // -49 تا -20
        if (isAllied || relationship >= 70) return "Allied";  // +70 تا +100
        if (relationship >= 20) return "Friendly";     // +20 تا +69
        return "Neutral";                              // -19 تا +19
    }

    /**
     * بررسی امکان تجارت و مأموریت (≥20 رابطه).
     */
    public boolean canTrade() {
        return relationship >= 20;
    }

    /**
     * بررسی امکان ارسال هدیه (در همه وضعیت‌های غیر دشمن).
     */
    public boolean canReceiveGift() {
        return relationship > -50;
    }

    /**
     * بررسی امکان درخواست اتحاد (≥70 رابطه).
     */
    public boolean canFormAlliance() {
        return relationship >= 70 && !isAllied;
    }

    /**
     * بررسی امکان درخواست صلح (فقط در حالت دشمن).
     */
    public boolean canRequestPeace() {
        return relationship <= -50;
    }

    /**
     * نمایش وضعیت با جزئیات برای tooltip در UI.
     */
    public String getDetailedStatus() {
        return String.format("[%s] %s (%d/100)",
                type.getDisplayName(),
                getStatus(),
                relationship);
    }
}