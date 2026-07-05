package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * هدف آموزشی فاز: پیاده‌سازی هوشمندانه ساختارهای داده‌ای با استفاده از Generics.
 * این کلاس یک مخزن مرکزی برای نگهداری و کوئری گرفتن از موجودیت‌های بازی است.
 * بر اساس اصول SOLID و برنامه‌نویسی تدافعی (Defensive Programming)، این کلاس
 * کپسوله‌سازی کامل داده‌ها و مصونیت در برابر خطاهای همزمانی را تضمین می‌کند.
 */
public class Repository<T> {
    private final List<T> items = new ArrayList<>();

    /**
     * افزودن عنصر جدید به مخزن با محافظت در برابر اشیای تهی.
     */
    public void add(T item) {
        if (item != null) {
            items.add(item);
        }
    }

    /**
     * حذف شرطی عناصر از مخزن به صورت امن.
     */
    public void removeIf(Predicate<T> filter) {
        if (filter != null) {
            items.removeIf(filter);
        }
    }

    /**
     * ایجاد یک استریم امن از اسنپ‌شات داده‌ها.
     * این کار از بروز خطای ConcurrentModificationException در کوئری‌های پیچیده جلوگیری می‌کند.
     */
    public Stream<T> stream() {
        return new ArrayList<>(items).stream();
    }

    /**
     * [گام ۵ - اصلاح معماری و کپسوله‌سازی]:
     * بازگرداندن یک کپی تدافعی (Defensive Copy) و فقط‌خواندنی از لیست.
     * این معماری تضمین می‌کند که:
     * ۱. هیچ کلاس بیرونی نمی‌تواند عناصر مخزن را بدون اجازه دستکاری کند (جلوگیری از Encapsulation Leakage).
     * ۲. حلقه زدن روی خروجی این متد در لایه‌های View و Controller کاملاً در برابر خطای کشنده
     * ConcurrentModificationException ایمن است.
     */
    public List<T> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * پاکسازی کامل مخزن.
     */
    public void clear() {
        items.clear();
    }

    /**
     * متد کمکی و سریع برای دریافت تعداد عناصر بدون نیاز به ساخت کپی از کل لیست.
     */
    public int size() {
        return items.size();
    }

    /**
     * متد کمکی و سریع برای بررسی خالی بودن مخزن.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}