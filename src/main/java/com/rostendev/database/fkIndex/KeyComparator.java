package com.rostendev.database.fkIndex;

@FunctionalInterface
public interface KeyComparator<T> {
    int compare(T a, T b);
}
