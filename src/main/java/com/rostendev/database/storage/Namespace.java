package com.rostendev.database.storage;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.database.NamespacePath;
import com.rostendev.database.index.IndexFile;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.schema.SchemaFile;
import com.rostendev.database.table.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Namespace implements AutoCloseable {

    private final NamespacePath namespacePath;
    private final Map<String, Table> tables = new HashMap<>();

    public Namespace(DatabasePath databasePath, String namespaceName) {
        this.namespacePath = new NamespacePath(databasePath, namespaceName);
    }

    public Table createTable(Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (schema.getTableName() == null) throw new IllegalArgumentException("El schema debe tener un nombre");
        schema.validate();
        DatabasePath tablePath = new DatabasePath(namespacePath.getDatabasePath().getDatabaseName(),
                namespacePath.getNamespaceName(), schema.getTableName());

        if (Files.exists(tablePath.getTablePath())) throw new IllegalArgumentException("La tabla ya existe");
        Files.createDirectory(tablePath.getTablePath());
        Table table = new Table(this, tablePath.getDatabaseName(), schema);
        tables.put(schema.getTableName(), table);
        return table;
    }

    public Table openTable(Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        DatabasePath tablePath = new DatabasePath(namespacePath.getDatabasePath().getDatabaseName(),
                namespacePath.getNamespaceName(), schema.getTableName());
        if (!Files.exists(tablePath.getTablePath())) throw new IllegalArgumentException("La tabla no existe: " + tablePath.getTableName());
        if (!Files.isDirectory(tablePath.getTablePath())) throw new IllegalArgumentException("La ruta de latabla no es un directorio: " + tablePath.getTableName());

        Table table= new Table(this, tablePath.getDatabaseName(), schema);
        tables.put(schema.getTableName(), table);
        return table;
    }


    public void openTables() throws IOException {

        try (var paths = Files.list(namespacePath.getNamespacePath())) {

            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    String tableName =path.getFileName().toString();
                    DatabasePath tablePath =new DatabasePath(
                                    namespacePath.getDatabasePath().getDatabaseName(),
                                    namespacePath.getNamespaceName(), tableName);

                    SchemaFile schemaFile =new SchemaFile(tablePath.getSchemaPath().toString());
                    Schema schema = schemaFile.read();
                    Table table =new Table(this, tablePath,schema);
                    tables.put(tableName, table);
                } catch (IOException e) {
                    throw new RuntimeException("No se pudo abrir la tabla: " + path,e);
                }
            });
        }
    }

    public boolean hasReferences(String referencedTable, String referencedColumn, Object value) throws IOException {
        for (Table table : tables.values()) {
            if (table.hasForeignKeyReference(referencedTable, referencedColumn,value)) return true;
        }
        return false;
    }

    public String getNamespaceName() {
        return namespacePath.getNamespaceName();
    }

    public Path getNamespacePath() {
        return namespacePath.getNamespacePath();
    }

    public Table getTable(String tableName) {
        return tables.get(tableName);
    }

    @Override
    public void close() throws Exception {

    }
}
