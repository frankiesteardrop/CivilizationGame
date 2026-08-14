package model;

/**
 * نگهداری وضعیت رابطه قبیله با بازیکن.
 *
 * بازه‌های رابطه طبق spec فاز دوم (اصلاح F-03):
 *   -100 تا -50 → Enemy
 *    -49 تا -20 → Displeased
 *    -19 تا +19 → Neutral
 *    +20 تا +69 → Friendly
 *    +70 تا +100 → Allied
 *
 * F-31: هر بار وضعیت رابطه تغییر می‌کند، notification به HUD ارسال می‌شود.
 */
public class Tribe {

    private final TribeType type;
    private int     relationship;
    private boolean isAllied;

    public Tribe(TribeType type) {
        this.type         = type;
        this.relationship = 0;
        this.isAllied     = false;
    }

    public TribeType getType()       { return type; }
    public int  getRelationship()    { return relationship; }
    public boolean isAllied()        { return isAllied; }
    public void setAllied(boolean a) { this.isAllied = a; }

    /**
     * تغییر مقدار رابطه با clamp -100 تا +100.
     *
     * F-31: اگر وضعیت (Status) تغییر کرد، یک notification به HUD ارسال می‌شود.
     * این به بازیکن اطلاع می‌دهد که رابطه وارد مرحله جدیدی شده است.
     */
    public void addRelationship(int amount) {
        if (amount == 0) return;

        // وضعیت قبل از تغییر برای مقایسه
        String previousStatus = getStatus();

        this.relationship = Math.max(-100, Math.min(100, this.relationship + amount));

        // اگر رابطه از allied threshold پایین آمد، اتحاد لغو می‌شود
        if (this.relationship < 70 && this.isAllied) {
            this.isAllied = false;
        }

        // F-31: notification فقط وقتی وضعیت واقعاً تغییر کرده است (نه هر تغییر عددی)
        String newStatus = getStatus();
        if (!previousStatus.equals(newStatus)) {
            String emoji = switch (newStatus) {
                case "Allied"     -> "🤝";
                case "Friendly"   -> "😊";
                case "Neutral"    -> "😐";
                case "Displeased" -> "😠";
                case "Enemy"      -> "⚔️";
                default           -> "🔔";
            };
            GameEventDispatcher.fireNotification(String.format(
                    "%s %s Tribe: %s → %s",
                    emoji, type.getDisplayName(), previousStatus, newStatus));
        }
    }

    /**
     * وضعیت رابطه بر اساس بازه‌های دقیق spec (اصلاح F-03).
     */
    public String getStatus() {
        if (relationship <= -50) return "Enemy";
        if (relationship <= -20) return "Displeased";
        if (isAllied || relationship >= 70) return "Allied";
        if (relationship >= 20) return "Friendly";
        return "Neutral";
    }

    public boolean canTrade()          { return relationship >= 20; }
    public boolean canReceiveGift()    { return relationship > -50; }
    public boolean canFormAlliance()   { return relationship >= 70 && !isAllied; }
    public boolean canRequestPeace()   { return relationship <= -50; }

    public String getDetailedStatus() {
        return String.format("[%s] %s (%d/100)",
                type.getDisplayName(), getStatus(), relationship);
    }
}