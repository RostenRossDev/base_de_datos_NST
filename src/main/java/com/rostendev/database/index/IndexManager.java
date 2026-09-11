package com.rostendev.database.index;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.RecordPointer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IndexManager {
    private final Map<Integer, BPlusTree> uniqueIndexes = new HashMap<>();
    public IndexManager(DatabasePath databasePath, Schema schema) throws IOException {
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            /*La PK ya tiene su propio B+Tree. Por lo tanto solamente creamos arboles para UNIQUE que
            no sean la PK*/

            if (!column.isUnique() || column.isPrimaryKey()) continue;
            BPlusTree tree = new BPlusTree(databasePath.getUniqueIndexPath(column.getName()), column.getType());
            uniqueIndexes.put(i, tree);
        }
    }

    public IndexEntry search(int columnIndex, Object value) throws IOException {
        if (value == null) return null;
        BPlusTree tree = uniqueIndexes.get(columnIndex);
        if (tree == null) throw new IllegalArgumentException("La columna no tiene indice UNIQUE " + columnIndex);
        return tree.search(value);
    }

    public void insert(int columnIndex, Object value, RecordPointer pointer) throws IOException {
        if (value == null) return;
        BPlusTree tree = uniqueIndexes.get(columnIndex);
        if (tree == null) throw new IllegalArgumentException("La columna no tiene indice UNIQUE " + columnIndex);
        tree.insert(value, pointer.getPageId(), pointer.getSlotId());
    }

    public void delete(int columnIndex, Object value) throws IOException {
        if (value == null) return;
        BPlusTree tree = uniqueIndexes.get(columnIndex);
        if (tree == null) throw new IllegalArgumentException("La columna no tiene indice UNIQUE " + columnIndex);
        tree.delete(value);
    }
}
