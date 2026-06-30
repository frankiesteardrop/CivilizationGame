package model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * هدف آموزشی فاز: پیاده‌سازی هوشمندانه ساختارهای داده‌ای با استفاده از Generics.
 * این کلاس یک مخزن مرکزی برای نگهداری و کوئری گرفتن از موجودیت‌های بازی است.
 */
public class Repository<T> {
    private final List<T> items = new ArrayList<>();

    public void add(T item) { items.add(item); }
    public void removeIf(Predicate<T> filter) { items.removeIf(filter); }
    public Stream<T> stream() { return items.stream(); }
    public List<T> getAll() { return items; }
    public void clear() { items.clear(); }
}