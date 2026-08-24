package model;
public interface TurnListener {
    void onTurnEnded(int newTurn);
    void onStarvationChanged(boolean isStarving);
}