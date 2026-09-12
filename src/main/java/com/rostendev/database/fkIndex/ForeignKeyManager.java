package com.rostendev.database.fkIndex;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.records.Record;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.RecordPointer;
import com.rostendev.database.table.Table;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ForeignKeyManager implements AutoCloseable {
    private final Map<Integer, RelationIndex<?,?>> foreignKeyIndexes = new HashMap<>();
    private final Table table;

    public ForeignKeyManager(Table table) throws IOException {

        DatabasePath databasePath = table.getDatabasePath();
        Schema schema = table.getSchema();
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isForeignKey()) continue;
            Table referencedTable = table.getReferencedTable(column);
            int referencedColumnIndex = referencedTable.getColumnIndex(column.getReferencedColumn());
            ColumnDefinition referencedColumn = referencedTable.getSchema().getColumns().get(referencedColumnIndex);
            DataType parentKeyType = referencedColumn.getType();
            DataType foreignKeyType = column.getType();
            if (parentKeyType != foreignKeyType) {
                throw new IllegalArgumentException("El tipo de la foreign key " + column.getName()+
                        "'("+foreignKeyType+"') no coincide con el tip ode la columna referenciuada"+
                        column.getReferencedColumn() + "'(" + parentKeyType +")'");
            }
            Path indexPath = databasePath.getTablePath().resolve(databasePath.getDatabaseName()+
                    "." + column.getName() + ".fk.index");
            RelationIndex<?, ?> relationIndex = new RelationIndex<>(indexPath, parentKeyType, foreignKeyType);
            foreignKeyIndexes.put(i, relationIndex);
        }
        this.table = table;
    }

    public boolean hasForeignKey(int columnIndex) {
        return foreignKeyIndexes.containsKey(columnIndex);
    }

    public <P,F>void insert(int columnIndex,P parentKey,F foreignKey,RecordPointer pointer) throws IOException {

        if (parentKey == null) throw new IllegalArgumentException("parentKey no puede ser null");
        if (foreignKey == null) return;
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        RelationIndex<P, F> index =getIndex(columnIndex);
        index.insert(parentKey,foreignKey,pointer);
    }

    public<P,F> void delete(int columnIndex,P parentKey, F foreignKey) throws IOException {
        if (parentKey == null) throw new IllegalArgumentException("parentKey no puede ser null");
        if (foreignKey == null) return;
        RelationIndex<P, F> index = getIndex(columnIndex);
        index.delete(parentKey,foreignKey);
    }

    public<P,F> boolean hasReferences(int columnIndex,P parentKey) throws IOException {
        if (parentKey == null) return false;
        RelationIndex<P, F> index = getIndex(columnIndex);
        return index.hasReference(parentKey);
    }

    public void validate(Record record) throws IOException {
        for (Map.Entry<Integer, RelationIndex<?,?>> entry: foreignKeyIndexes.entrySet()) {
            int columnIndex = entry.getKey();
            Object foreignKey = record.get(columnIndex);
            //FK
            if (foreignKey == null) continue;
            ColumnDefinition column = record.getSchema().getColumns().get(columnIndex);

            //la fk debe coincidir con la columna fererenciada
            Table referencedTable = table.getReferencedTable(column);
            if (referencedTable.find(foreignKey) == null) {
                throw new IllegalArgumentException("La foreign key '"+column.getName()+
                        "' referencia un registro inexistente: " + foreignKey);
            }
        }
    }

    private <P, F> boolean hasReferencesInternal(RelationIndex<P, F> index, Object parentKey) throws IOException {
        return index.hasReference((P) parentKey);
    }

    @SuppressWarnings("unchecked")
    private<P, F> RelationIndex<P, F> getIndex(int columnIndex) {
        RelationIndex<?, ?> index = foreignKeyIndexes.get(columnIndex);
        if (index == null) throw new IllegalArgumentException(
                    "La columna no tiene índice FOREIGN KEY: "+ columnIndex);
        return (RelationIndex<P, F>) index;
    }

    @Override
    public void close() throws Exception {
        for (RelationIndex<?, ?> index : foreignKeyIndexes.values()) {
            index.close();
        }
    }
}
