package com.rostendev.database.database;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.storage.Namespace;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DataBase implements AutoCloseable{

    private final Path databasePath;
    private final String databaseName;

    private DataBase (String databaseName) {
        validateName(databaseName, "databaseName");
        this.databaseName = databaseName;
        this.databasePath = Paths.get(Constants.ROOT_DIRECTORY, databaseName);
    }

    public Namespace createNamespace(String namespaceName) throws IOException {
        DatabasePath databasePath = new DatabasePath(databaseName, namespaceName);

        NamespacePath namespacePath = new NamespacePath(databasePath, namespaceName);
        if (Files.exists(namespacePath.getNamespacePath()))
            throw new IllegalArgumentException("El namespace ya existe: "+ namespacePath.getNamespacePath());

        Files.createDirectory(namespacePath.getNamespacePath());
        return new Namespace(databasePath, namespaceName);
    }

    public static DataBase create(String databaseName) throws IOException {
        DataBase dataBase = new DataBase(databaseName);
        if (Files.exists(dataBase.databasePath))
            throw new IllegalArgumentException("La base de datos ya existe: " + dataBase.databasePath);
        Files.createDirectory(dataBase.databasePath);
        return dataBase;
    }

    public static DataBase open(String databaseName) throws IOException {
        DataBase dataBase = new DataBase(databaseName);
        if (!Files.exists(dataBase.databasePath))
            throw new IllegalArgumentException("La base de datos no existe: " + dataBase.databasePath);

        if (!Files.isDirectory(dataBase.databasePath))
            throw new IOException("La ruta de la base de datos no es un directorio: " + dataBase.databasePath);

        return dataBase;
    }

    private static void validateName(String name, String field) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException(field + " no puede ser nulo o vacio.");

        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException(field + " contiene caracteres invalidos.");

    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public void close() throws Exception {

    }
}
