package network.messages.game;

import model.WatReport;
import network.messages.Message;

import java.util.List;

/**
 * Sent by the server to a specific client at the start of their turn,
 * listing all combat events that occurred during opponents' turns.
 *
 * <p>The client should display each report as a notification so the player
 * knows what happened to their units and structures while they were waiting.
 */
public class WatReportBroadcast extends Message {

    private final List<WatReport> reports;

    public WatReportBroadcast(List<WatReport> reports) {
        super("WAT_REPORT");
        this.reports = reports;
    }

    public List<WatReport> getReports() { return reports; }
}