package model;

public class Explorer extends Unit {
    public Explorer(int q, int r) {
        // maxAP=6 (بیشترین), foodConsumption=2, visionRadius=4 (بیشترین)
        super(q, r, 6, 2, 4);
    }
}