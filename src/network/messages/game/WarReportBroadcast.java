package network.messages.game;

import model.WarReport;
import network.messages.Message;
import java.util.List;

public class WarReportBroadcast extends Message {

    private final List<WarReport> reports;

    public WarReportBroadcast(List<WarReport> reports) {
        super("WAR_REPORT");
        this.reports = reports;
    }

    public List<WarReport> getReports() { return reports; }
}