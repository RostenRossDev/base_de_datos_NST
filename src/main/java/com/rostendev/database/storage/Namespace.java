package com.rostendev.database.storage;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.database.NamespacePath;
import com.rostendev.database.index.IndexFile;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.table.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Namespace implements AutoCloseable {

    private final NamespacePath namespacePath;

    public Namespace(DatabasePath databasePath, String namespaceName) {
        this.namespacePath = new NamespacePath(databasePath, namespaceName);
    }

    public Table createTable(Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (schema.getTableName() == null) throw new IllegalArgumentException("El schema debe tener un nombre");
        DatabasePath tablePath = new DatabasePath(namespacePath.getDatabasePath().getDatabaseName(),
                namespacePath.getNamespaceName(), schema.getTableName());

        if (Files.exists(tablePath.getTablePath())) throw new IllegalArgumentException("La tabla ya existe");
        Files.createDirectory(tablePath.getTablePath());
        return new Table(tablePath.getDatabaseName(), tablePath.getNamespaceName(), schema);
    }

    public Table openTable(Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        DatabasePath tablePath = new DatabasePath(namespacePath.getDatabasePath().getDatabaseName(),
                namespacePath.getNamespaceName(), schema.getTableName());
        if (!Files.exists(tablePath.getTablePath())) throw new IllegalArgumentException("La tabla no existe: " + tablePath.getTableName());
        if (!Files.isDirectory(tablePath.getTablePath())) throw new IllegalArgumentException("La ruta de latabla no es un directorio: " + tablePath.getTableName());

        return new Table(tablePath.getDatabaseName(), tablePath.getNamespaceName(), schema);
    }


    public String getNamespaceName() {
        return namespacePath.getNamespaceName();
    }

    public Path getNamespacePath() {
        return namespacePath.getNamespacePath();
    }


    @Override
    public void close() throws Exception {

    }
}
