package com.rostendev.database.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Schema {
    private final String tableName;
    private List<ColumnDefinition> colums = new ArrayList<>();

    public Schema(String tableName) {
        this.tableName = tableName;
    }

    public void addColumn(ColumnDefinition column) {
        if (column == null) throw new IllegalArgumentException("La columna no puede ser null");

        if (column.isUnique() && !supportsUnique(column.getType())) {
            throw new IllegalArgumentException("El tipo " +column.getType() +" no admite UNIQUE");
        }

        if (column.getName() == null ||column.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre de la columna no puede ser null o vacío");
        }

        if (column.getType() == null) {
            throw new IllegalArgumentException("La columna '" +column.getName() +"' debe tener un tipo");
        }

        if (column.getLength() != null) {
            if (column.getLength() <= 0) {
                throw new IllegalArgumentException("El length de la columna '" +column.getName() +"' debe ser mayor a 0");
            }
            if (column.getType() != DataType.STRING) {
                throw new IllegalArgumentException("La columna '" +column.getName() +"' tiene length pero no es STRING");
            }
        }

        for (ColumnDefinition existing : colums) {
            if (existing.getName().equals(column.getName())) {
                throw new IllegalArgumentException("La columna ya existe: " +column.getName());
            }
        }

        colums.add(column);
    }

    public String getTableName(){
        return tableName;
    }

    public List<ColumnDefinition> getColumns() {
        return Collections.unmodifiableList(colums);
    }

    public void validate() {

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la tabla no puede ser null o vacío");
        }
        if (colums.isEmpty()) {
            throw new IllegalArgumentException("La tabla debe tener al menos una columna");
        }

        int primaryKeys = 0;

        for (ColumnDefinition column : colums) {
            if (column.isPrimaryKey()) {
                primaryKeys++;
            }

            if (column.isPrimaryKey() && column.isNullable()) {
                throw new IllegalArgumentException("La clave primaria no puede ser nullable: " +column.getName());
            }
        }

        if (primaryKeys > 1) {
            throw new IllegalArgumentException("La tabla no puede tener más de una clave primaria");
        }
    }

    private boolean supportsUnique(DataType type) {

        return switch (type) {
            case BYTE,
                 SHORT,
                 INT,
                 LONG,
                 DOUBLE,
                 STRING,
                 BIGDECIMAL,
                 BIGINT -> true;

            case BOOLEAN -> false;
        };
    }
}
