package model;

public class Worker extends Unit {
    private boolean isStationed;

    public Worker(int q, int r) {
        // اختصاص مقادیر: AP = 2، مصرف غذا = 1، شعاع دید = 1
        super(q, r, 2, 1, 1);
        this.isStationed = false; // در ابتدا کارگر بیکار است
    }

    public boolean isStationed() { return isStationed; }

    public void setStationed(boolean stationed) {
        this.isStationed = stationed;
    }
}