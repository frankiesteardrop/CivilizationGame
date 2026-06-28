package model;

public class BorderExpander extends Unit {
    public BorderExpander(int q, int r) {
        super(q, r, 2, 2, 1);
    }

    /**
     * اجرای ایمن رویداد مرزگشایی.
     * خروجی boolean به کنترلر اطمینان می‌دهد که عملیات انجام شده است.
     */
    public boolean expandBorder(GameMap map) {
        if (!this.isAlive()) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());

        // گارد امنیتی مدل: محافظت از قوانین مه‌جنگ مستقل از لایه گرافیک
        if (currentHex == null || !currentHex.isExplored()) {
            return false;
        }

        // اعمال منطق گسترش
        map.expandBorderAt(this.getQ(), this.getR());
        map.updateFogOfWar();

        // مصرف (Consume) کامل یونیت
        this.kill();
        return true;
    }
}