package model;

import java.util.ArrayList;
import java.util.List;

public class GameMap {
    private List<Hex> hexes;
    private int radius; // شعاع نقشه شش ضلعی

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new ArrayList<>();
        generateBasicMap();
    }

    // یک متد ساده برای تولید نقشه اولیه (بدون بخش امتیازی مپ جنریتور رویه ای)
    private void generateBasicMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                // فعلا همه جا رو دشت بدون منبع میذاریم تا در گام های بعدی منطق چیدمان رو کامل کنیم
                hexes.add(new Hex(q, r, TerrainType.PLAINS, ResourceType.NONE, 0));
            }
        }
    }

    public List<Hex> getHexes() {
        return hexes;
    }

    public Hex getHexAt(int q, int r) {
        for (Hex hex : hexes) {
            if (hex.getQ() == q && hex.getR() == r) {
                return hex;
            }
        }
        return null;
    }
}