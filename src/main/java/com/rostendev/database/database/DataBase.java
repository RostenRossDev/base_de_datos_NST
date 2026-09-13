package com.rostendev.database.database;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.Namespace;
import com.rostendev.database.table.Table;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataBase implements AutoCloseable{

    private final Path databasePath;
    private final String databaseName;
    private final Map<String, Namespace> namespaces = new HashMap<>();

    private DataBase (String databaseName) {
        validateName(databaseName, "databaseName");
        this.databaseName = databaseName;
        this.databasePath = Paths.get(Constants.ROOT_DIRECTORY, databaseName);
    }

    public Namespace createNamespace(String namespaceName) throws IOException {
        DatabasePath databasePath = new DatabasePath(databaseName, namespaceName);
        NamespacePath namespacePath = new NamespacePath(databasePath, namespaceName);
        if (Files.exists(namespacePath.getNamespacePath()))
            throw new IllegalArgumentException("El namespace ya existe: " +namespacePath.getNamespacePath());
        Files.createDirectory(namespacePath.getNamespacePath());
        Namespace namespace =new Namespace(databasePath, namespaceName);
        namespaces.put(namespaceName, namespace);
        return namespace;
    }

    public Table openOrCreateTable(Schema schema, String namespaceName) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no pude ser nulo");
        String tableName = schema.getTableName();
        Namespace namespace = namespaces.get(namespaceName);
        if (namespace == null) throw new IllegalArgumentException("El namespace no existe");
        // construir ruta: database / namespace / table
        DatabasePath tablePath = new DatabasePath(databaseName,namespaceName,tableName);
        if (Files.exists(tablePath.getTablePath())) {
            Table table =new Table(namespace,tablePath,schema);
            return table;
        }
        //No existe -> crear
        Files.createDirectories(tablePath.getTablePath());
        Table table = new Table(namespace, tablePath, schema);
        return table;
    }

    public static DataBase create(String databaseName,String namespaceName) throws IOException {
        DataBase dataBase = new DataBase(databaseName);
        if (Files.exists(dataBase.databasePath)) {
            throw new IllegalArgumentException("La base de datos ya existe: " +dataBase.databasePath);
        }
        Files.createDirectories(dataBase.databasePath);
        DatabasePath namespaceDatabasePath = new DatabasePath(databaseName,namespaceName);
        Namespace namespace = new Namespace(namespaceDatabasePath,namespaceName);
        dataBase.namespaces.put(namespaceName,namespace);
        return dataBase;
    }

    public static DataBase openOrCreate(String databaseName, String namespace, String tableName) throws IOException {
        if (databaseName == null || databaseName.isEmpty())
            throw new IOException("databaseName no puede ser nulo");
        if (namespace == null || namespace.isEmpty())
            throw new IOException("namespace no puede ser nulo");
        DataBase dataBase = new DataBase(databaseName);
        if (!Files.exists(dataBase.databasePath))
            return create(databaseName, namespace);
        if (tableName == null || tableName.isEmpty())
            throw new IOException("tableName no puede ser nulo");
        if (!Files.isDirectory(dataBase.databasePath))
            throw new IOException("La ruta de la base de datos no es un directorio: " +dataBase.databasePath);
        return open(databaseName, namespace, tableName);
    }

    public static DataBase open(String databaseName, String namespaceName, String tableName) throws IOException {
        DataBase dataBase = new DataBase(databaseName);
        if (!Files.exists(dataBase.databasePath))
            throw new IllegalArgumentException("La base de datos no existe: " + dataBase.databasePath);
        if (!Files.isDirectory(dataBase.databasePath))
            throw new IOException("La ruta de la base de datos no es un directorio: " + dataBase.databasePath);
        DatabasePath namespaceDatabasePath = new DatabasePath(databaseName, namespaceName, tableName);
        Path namespacePath = namespaceDatabasePath.getDataPath();
        if (!Files.exists(namespacePath)) throw new IllegalArgumentException("El namespace no existe: " + namespaceName);
        Namespace namespace = new Namespace(namespaceDatabasePath, namespaceName);
        dataBase.namespaces.put(namespaceName, namespace);
        return dataBase;
    }

    private static void validateName(String name, String field) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException(field + " no puede ser nulo o vacio.");

        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException(field + " contiene caracteres invalidos.");

    }

    public Namespace getNamespace(String namespaceName) {
        return namespaces.get(namespaceName);
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
