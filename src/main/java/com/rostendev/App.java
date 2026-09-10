package com.rostendev;

import com.rostendev.database.database.DataBase;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceEntry;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceMap;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.records.Record;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.*;
import com.rostendev.database.table.Table;

import javax.swing.table.TableCellEditor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws Exception {

        System.out.println("=== CREANDO DATABASE ===");

        DataBase db = DataBase.create("universidad");

        System.out.println("Database creada:");
        System.out.println(db.getDatabasePath());


        System.out.println("\n=== CREANDO NAMESPACE ===");

        Namespace namespace = db.createNamespace("public");

        System.out.println("Namespace creado:");
        System.out.println(namespace.getNamespacePath());


        System.out.println("\n=== CREANDO SCHEMA ===");

        Schema schema = new Schema("persona");

        schema.addColumn(
                new ColumnDefinition(
                        "id",
                        DataType.INT,
                        null,
                        false,
                        true,
                        false,
                        true
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "nombre",
                        DataType.STRING,
                        100,
                        false,
                        false,
                        false,
                        false
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "edad",
                        DataType.INT,
                        null,
                        true,
                        false,
                        false,
                        false
                )
        );

        System.out.println("Schema creado:");
        System.out.println("Tabla: " + schema.getTableName());

        for (ColumnDefinition column : schema.getColumns()) {
            System.out.println(
                    " - "
                            + column.getName()
                            + " | "
                            + column.getType()
                            + " | length="
                            + column.getLength()
                            + " | nullable="
                            + column.isNullable()
                            + " | primaryKey="
                            + column.isPrimaryKey()
                            + " | foreignKey="
                            + column.isForeignKey()
                            + " | unique="
                            + column.isUnique()
            );
        }


        System.out.println("\n=== CREANDO TABLE ===");

        Table table = namespace.createTable(schema);

        System.out.println("Table creada:");
        System.out.println(table.getName());


        System.out.println("\n=== VERIFICANDO ESTRUCTURA FISICA ===");

        Path tablePath = namespace
                .getNamespacePath()
                .resolve(schema.getTableName());

        Path dataPath =
                tablePath.resolve(schema.getTableName() + ".data");

        Path schemaPath =
                tablePath.resolve(schema.getTableName() + ".schema");

        Path indexPath =
                tablePath.resolve(schema.getTableName() + ".index");

        Path fsmPath =
                tablePath.resolve(schema.getTableName() + ".fsm");


        System.out.println(
                "Database : "
                        + db.getDatabasePath()
                        + " -> "
                        + Files.exists(db.getDatabasePath())
        );

        System.out.println(
                "Namespace: "
                        + namespace.getNamespacePath()
                        + " -> "
                        + Files.exists(namespace.getNamespacePath())
        );

        System.out.println(
                "Table    : "
                        + tablePath
                        + " -> "
                        + Files.exists(tablePath)
        );

        System.out.println(
                "Data     : "
                        + dataPath
                        + " -> "
                        + Files.exists(dataPath)
        );

        System.out.println(
                "Schema   : "
                        + schemaPath
                        + " -> "
                        + Files.exists(schemaPath)
        );

        System.out.println(
                "Index    : "
                        + indexPath
                        + " -> "
                        + Files.exists(indexPath)
        );

        System.out.println(
                "FSM      : "
                        + fsmPath
                        + " -> "
                        + Files.exists(fsmPath)
        );


        System.out.println("\n=== ESTRUCTURA COMPLETA ===");

        Files.walk(db.getDatabasePath())
                .forEach(System.out::println);


        table.close();
        namespace.close();
        db.close();

        System.out.println("\n=== TEST FINALIZADO ===");
    }

    private static void deleteDirectory(File directory) {

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                file.delete();
            }
        }

        directory.delete();
    }
}
