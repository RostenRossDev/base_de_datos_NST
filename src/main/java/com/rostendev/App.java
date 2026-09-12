package com.rostendev;

import com.rostendev.database.database.DataBase;
import com.rostendev.database.index.IndexEntry;
import com.rostendev.database.schema.SchemaFile;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {

    static void main() throws IOException {

        // =========================================================
        // 1. DEFINICIÓN DEL SCHEMA
        // =========================================================

        Schema schema = new Schema("users");

        schema.addColumn(
                new ColumnDefinition(
                        "id",
                        DataType.INT,
                        null,
                        false,
                        true,
                        false,
                        true,
                        null,
                        null
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "name",
                        DataType.STRING,
                        50,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "age",
                        DataType.INT,
                        null,
                        true,
                        false,
                        false,
                        false,
                        null,
                        null
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "active",
                        DataType.BOOLEAN,
                        null,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null
                )
        );

        schema.validate();

        // =========================================================
        // 2. ESTRUCTURA FÍSICA DE LA BASE
        // =========================================================

        Path databasePath = Path.of("database");
        Path tablePath = databasePath.resolve(schema.getTableName());

        Files.createDirectories(tablePath);

        Path schemaPath = tablePath.resolve("schema");
        Path dataPath = tablePath.resolve("data.nst");
        Path indexPath = tablePath.resolve("index.idx");

        System.out.println();
        System.out.println("=== DATABASE ===");
        System.out.println("Database : " + databasePath.toAbsolutePath());
        System.out.println("Table    : " + tablePath.toAbsolutePath());
        System.out.println("Schema   : " + schemaPath.toAbsolutePath());
        System.out.println("Data     : " + dataPath.toAbsolutePath());
        System.out.println("Index    : " + indexPath.toAbsolutePath());

        // =========================================================
        // 3. MOSTRAR EL SCHEMA
        // =========================================================

        System.out.println();
        System.out.println("=== SCHEMA ===");
        System.out.println("Tabla: " + schema.getTableName());

        for (ColumnDefinition column : schema.getColumns()) {
            System.out.println(
                    column.getName()
                            + " -> "
                            + column.getType()
                            + " | length=" + column.getLength()
                            + " | nullable=" + column.isNullable()
                            + " | primaryKey=" + column.isPrimaryKey()
                            + " | unique=" + column.isUnique()
            );
        }

        System.out.println();
        System.out.println("Estructura física creada correctamente.");
    }
}
