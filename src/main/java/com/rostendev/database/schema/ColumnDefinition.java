package com.rostendev.database.schema;

public class ColumnDefinition {
    private final String name;
    private final DataType type;
    private final Integer length;

    private final boolean nullable;
    private final boolean primaryKey;
    private final boolean foreignKey;
    private final boolean unique;

    private final String referencedTable;
    private final String referencedColumn;

    public ColumnDefinition(String name, DataType type, Integer length, boolean nullable,
            boolean primaryKey, boolean foreignKey, boolean unique, String referencedTable,
            String referencedColumn) {

        this.name = name;
        this.type = type;
        this.length = length;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.foreignKey = foreignKey;
        this.unique = unique;
        this.referencedTable = referencedTable;
        this.referencedColumn = referencedColumn;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public String getReferencedTable() {
        return referencedTable;
    }

    public String getReferencedColumn() {
        return referencedColumn;
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
