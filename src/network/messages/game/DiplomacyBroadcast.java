package network.messages.game;

import network.messages.Message;

public class DiplomacyBroadcast extends Message {


    private final String eventType;

    private final String initiatorId;
    private final String initiatorName;
    private final String targetId;
    private final String targetName;

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