package model.mission;

public interface MissionState {
    boolean canAccept();
    boolean canDeliver();
    String getDisplayName();
}