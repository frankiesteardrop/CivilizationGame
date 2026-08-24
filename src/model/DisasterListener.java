package model;
import java.util.List;
public interface DisasterListener { void onDisasterTriggered(String type, Hex center, List<Hex> affected); }