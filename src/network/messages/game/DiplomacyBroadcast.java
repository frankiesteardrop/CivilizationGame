package network.messages.game;

import network.messages.Message;

/**
 * Broadcast from the server to all (or specific) clients when a diplomatic
 * event occurs: war declaration, alliance request, alliance formed, alliance broken.
 *
 * <p>Global events (war declaration, alliance formed/broken) are sent to ALL clients.
 * Private events (alliance request) are sent only to the target client.
 */
public class DiplomacyBroadcast extends Message {

    /**
     * Type of diplomatic event. Known values:
     * "WAR_DECLARED", "ALLIANCE_REQUESTED", "ALLIANCE_FORMED", "ALLIANCE_BROKEN",
     * "ALLIANCE_REJECTED"
     */
    private final String eventType;

    private final String initiatorId;
    private final String initiatorName;
    private final String targetId;
    private final String targetName;

    /** Human-readable announcement text suitable for display in the game notification area. */
    private final String announcementText;

    public DiplomacyBroadcast(String eventType,
                              String initiatorId, String initiatorName,
                              String targetId,    String targetName,
                              String announcementText) {
        super("DIPLOMACY_EVENT");
        this.eventType        = eventType;
        this.initiatorId      = initiatorId;
        this.initiatorName    = initiatorName;
        this.targetId         = targetId;
        this.targetName       = targetName;
        this.announcementText = announcementText;
    }

    public String getEventType()        { return eventType; }
    public String getInitiatorId()      { return initiatorId; }
    public String getInitiatorName()    { return initiatorName; }
    public String getTargetId()         { return targetId; }
    public String getTargetName()       { return targetName; }
    public String getAnnouncementText() { return announcementText; }
}