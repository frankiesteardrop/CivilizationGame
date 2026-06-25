package model;

public class BorderExpander extends Unit {
    public BorderExpander(int q, int r) {
        // اختصاص مقادیر: AP = 2، مصرف غذا = 2، شعاع دید = 1
        super(q, r, 2, 2, 1);
    }

    public void expandBorder() {
        // لاجیک گسترش مرز در گام‌های بعدی در نقشه اعمال می‌شود
        // فعلا فقط مکانیک حذف (Consume) شدن یونیت پس از استفاده را پیاده می‌کنیم
        this.kill();
    }
}