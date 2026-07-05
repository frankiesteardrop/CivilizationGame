package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class Repository<T> {
    private final List<T> items = new ArrayList<>();

    public void add(T item) {
        if (item != null) {
            items.add(item);
        }
    }


    public void removeIf(Predicate<T> filter) {
        if (filter != null) {
            items.removeIf(filter);
        }
    }


    public Stream<T> stream() {
        return new ArrayList<>(items).stream();
    }


    public List<T> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public void clear() {
        items.clear();
    }


    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}