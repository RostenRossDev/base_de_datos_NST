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

    public void addColumn(ColumnDefinition colum) {
        colums.add(colum);
    }

    public String getTableName(){
        return tableName;
    }

    public List<ColumnDefinition> getColumns() {
        return Collections.unmodifiableList(colums);
    }
}
