package com.rostendev.database.fkIndex;

import com.rostendev.database.storage.RecordPointer;

public class RelationEntry<P, F> {
    private final P parentKey;
    private final F foreignKey;
    private final RecordPointer pointer;

    public RelationEntry( P parentKey, F foreignKey, RecordPointer pointer) {
        if (parentKey == null) throw new IllegalArgumentException("parentKey no puede ser null");
        if (foreignKey == null) throw new IllegalArgumentException("foreignKey no puede ser null");
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");

        this.parentKey = parentKey;
        this.foreignKey = foreignKey;
        this.pointer = pointer;
    }

    public P getParentKey() {
        return parentKey;
    }

    public F getForeignKey() {
        return foreignKey;
    }

    public RecordPointer getPointer() {
        return pointer;
    }

    @Override
    public String toString() {
        return "PARENT=" + parentKey
                + ", FK=" + foreignKey
                + ", POINTER=" + pointer;
    }
}
