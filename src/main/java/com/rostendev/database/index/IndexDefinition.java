package com.rostendev.database.index;

import com.rostendev.database.schema.DataType;

public class IndexDefinition {
    private final String columnName;
    private final DataType type;
    private final boolean unique;

    public IndexDefinition(String columnName,DataType type,boolean unique) {
        this.columnName = columnName;
        this.type = type;
        this.unique = unique;
    }

    public String getColumnName() {
        return columnName;
    }

    public DataType getType() {
        return type;
    }

    public boolean isUnique() {
        return unique;
    }
}
