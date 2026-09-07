package com.rostendev.database.database;

import com.rostendev.database.constants.Constants;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DatabasePath {
    private final Path databasePath;
    private final Path namespacesPath;
    private final Path  tablePath;

    private final String databaseName;
    private final String namespaceName;
    private final String tableName;

    public DatabasePath(String dbName, String namespacesName, String tableName){

        validateName(dbName, "databaseName");
        validateName(namespacesName, "namespaceName");
        validateName(tableName, "tableName");

        this.databaseName = dbName;
        this.namespaceName = namespacesName;
        this.tableName = tableName;
        this.databasePath =  Paths.get(Constants.ROOT_DIRECTORY, dbName);
        this.namespacesPath =  databasePath.resolve(namespacesName);
        this.tablePath =  databasePath.resolve(tableName);
    }
    private static void validateName(String name, String field){
        if (name == null || name.isBlank()) throw new IllegalArgumentException(field + " no puede ser nulo o vacio.");
        /*
         * No permitimos separadores de directorio
         * ni nombres especiales.
         */
        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException(field + "contiene caractes invalidos");
    }
    public Path getSchemaPath() {
        return tablePath.resolve("schema_" + tablePath);
    }
    public Path getDataPath() {
        return tablePath.resolve("data");
    }
    public Path getIndexPath() {
        return tablePath.resolve("index");
    }

    public Path getDbName() {
        return databasePath;
    }

    public Path getNamespacesName() {
        return namespacesPath;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public Path getNamespacesPath() {
        return namespacesPath;
    }

    public Path getTablePath() {
        return tablePath;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public String getTableName() {
        return tableName;
    }
}
