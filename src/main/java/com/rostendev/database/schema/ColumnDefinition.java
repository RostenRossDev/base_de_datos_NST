package com.rostendev.database.schema;

public class ColumnDefinition {
    private final String name;
    private final DataType type;
    private final Integer length;

    private final boolean nullable;
    private final boolean primaryKey;
    private final boolean foreignKey;
    private final boolean unique;

    public ColumnDefinition(String name, DataType type, Integer length,
            boolean nullable, boolean primaryKey,
            boolean foreignKey, boolean unique) {

        this.name = name;
        this.type = type;
        this.length = length;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.foreignKey = foreignKey;
        this.unique = unique;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public Integer getLength() {
        return length;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isForeignKey() {
        return foreignKey;
    }

    public boolean isUnique() {
        return unique;
    }
}
