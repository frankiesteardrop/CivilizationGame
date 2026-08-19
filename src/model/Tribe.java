package model;

import model.mission.Mission;
import model.state.tribe.*;

public class Tribe {
    private final TribeType type;
    private int relationship;
    private boolean isAllied;
    private TribeState state; // State Pattern
    private Mission mission;
    private int missionCooldown;
    private boolean tradeBonus;

    public Tribe(TribeType type) {
        this.type = type;
        this.relationship = 0;
        this.isAllied = false;
        this.tradeBonus = false;
        updateState();
    }

    public TribeType getType() { return type; }
    public int getRelationship() { return relationship; }
    public boolean isAllied() { return isAllied; }
    public void setAllied(boolean a) { this.isAllied = a; updateState(); }

    public TribeState getState() { return state; }
    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }
    public int getMissionCooldown() { return missionCooldown; }
    public void setMissionCooldown(int cooldown) { this.missionCooldown = cooldown; }
    public void decrementMissionCooldown() { if (missionCooldown > 0) missionCooldown--; }

    public boolean hasTradeBonus() { return tradeBonus; }
    public void setTradeBonus(boolean b) { this.tradeBonus = b; }

    public void addRelationship(int amount) {
        if (amount == 0) return;
        String prevStatus = state.getName();
        this.relationship = Math.max(-100, Math.min(100, this.relationship + amount));
        if (this.relationship < 70 && this.isAllied) this.isAllied = false;

        updateState();

        if (!prevStatus.equals(state.getName())) {
            GameEventDispatcher.fireNotification("🔔 " + type.getDisplayName() + " Tribe: " + prevStatus + " → " + state.getName());
        }
    }

    private void updateState() {
        if (relationship <= -50) this.state = new EnemyState();
        else if (relationship <= -20) this.state = new DispleasedState();
        else if (isAllied || relationship >= 70) this.state = new AlliedState();
        else if (relationship >= 20) this.state = new FriendlyState();
        else this.state = new NeutralState();
    }

    /**
     * اصلاح C1: فراخوانی پس از Load برای بازسازی TribeState از روی مقادیر primitive.
     * TribeStateAdapter این کار را انجام می‌دهد، اما این متد به عنوان safety net باقی می‌ماند.
     */
    public void postLoad() {
        updateState();
    }

    // Delegation to State
    public boolean canTrade() { return state.canTrade(); }
    public boolean canReceiveGift() { return state.canReceiveGift(); }
    public boolean canFormAlliance() { return state.canFormAlliance(); }
    public boolean canRequestPeace() { return state.canRequestPeace(); }

    public String getDetailedStatus() { return String.format("[%s] %s (%d/100)", type.getDisplayName(), state.getName(), relationship); }
}