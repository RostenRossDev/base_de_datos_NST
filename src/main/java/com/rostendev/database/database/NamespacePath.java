package com.rostendev.database.database;

import javax.naming.Name;
import java.nio.file.Path;

public class NamespacePath {
    private final DatabasePath databasePath;
    private final String namespaceName;
    private final Path namespacePath;

    public NamespacePath(DatabasePath databasePath, String namespaceName){
        if (databasePath == null) throw new IllegalArgumentException("databasePath no puede ser null");
        validateName(namespaceName, "namespaceName");
        this.databasePath = databasePath;
        this.namespaceName = namespaceName;
        this.namespacePath = databasePath.getDatabasePath().resolve(namespaceName);
    }

    private static void validateName(String name, String field) {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException(field + " no puede ser nulo o vacio.");

        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException(field + " contiene caracteres invalidos.");

    }

    public DatabasePath getDatabasePath() {
        return databasePath;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public Path getNamespacePath() {
        return namespacePath;
    }
}
