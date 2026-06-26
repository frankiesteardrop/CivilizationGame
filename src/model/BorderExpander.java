package model;

public class BorderExpander extends Unit {
    public BorderExpander(int q, int r) {
        super(q, r, 2, 2, 1);
    }

    /**
     * اجرای کامل رویداد مرزگشایی روی نقشه بازی و حذف یونیت از چرخه حیات
     */
    public void expandBorder(GameMap map) {
        if (!this.isAlive()) return;

        // اعمال منطق گسترش روی مختصات فعلی این یونیت
        map.expandBorderAt(this.getQ(), this.getR());

        // مه‌جنگ را بلافاصله پس از تغییر مرزها به‌روزرسانی می‌کنیم
        map.updateFogOfWar();

        // حذف کامل (Consume) شدن یونیت پس از استفاده
        this.kill();
    }
}