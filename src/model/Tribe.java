package model;

import model.mission.Mission;

/**
 * نگهداری وضعیت رابطه قبیله با بازیکن.
 */
public class Tribe {

    private final TribeType type;
    private int     relationship;
    private boolean isAllied;

    // فیلدهای جدید سیستم مأموریت
    private Mission mission;
    private int     missionCooldown;
    private boolean tradeBonus; // برای جایزه قبیله تجاری

    public Tribe(TribeType type) {
        this.type         = type;
        this.relationship = 0;
        this.isAllied     = false;
        this.mission      = null;
        this.missionCooldown = 0;
        this.tradeBonus   = false;
    }

    public TribeType getType()       { return type; }
    public int  getRelationship()    { return relationship; }
    public boolean isAllied()        { return isAllied; }
    public void setAllied(boolean a) { this.isAllied = a; }

    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }

    public int getMissionCooldown() { return missionCooldown; }
    public void setMissionCooldown(int cooldown) { this.missionCooldown = cooldown; }
    public void decrementMissionCooldown() { if (missionCooldown > 0) missionCooldown--; }

    public boolean hasTradeBonus() { return tradeBonus; }
    public void setTradeBonus(boolean b) { this.tradeBonus = b; }

    public void addRelationship(int amount) {
        if (amount == 0) return;
        String previousStatus = getStatus();
        this.relationship = Math.max(-100, Math.min(100, this.relationship + amount));

        if (this.relationship < 70 && this.isAllied) {
            this.isAllied = false;
        }

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