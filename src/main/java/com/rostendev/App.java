package com.rostendev;


import com.rostendev.database.constants.Constants;
import com.rostendev.database.database.DataBase;

import com.rostendev.database.database.DatabasePath;
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
import com.rostendev.database.wal.WalFile;
import com.rostendev.database.wal.WalRecord;
import com.rostendev.database.wal.WalRecovery;


import javax.swing.table.TableCellEditor;
import javax.xml.crypto.Data;

import java.io.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.List;


/**
 * Hello world!
 *
 */
public class App {

    static String db ="test-db", namespace ="test", table ="users";
    static DatabasePath databasePath;

    static void main() throws IOException {
//        DataBase dataBase = abrirDB();
//        Schema schema = new Schema(table);
//        Namespace namespace = new Namespace(databasePath, databasePath.getNamespaceName());
//        schema.addColumn(new ColumnDefinition("id",DataType.INT,null,false,true,
//                        false,false,null,null));
//        schema.addColumn(new ColumnDefinition("name",DataType.STRING,100,false,
//                        false,false,false,null,null));
//        schema.addColumn(new ColumnDefinition("email",DataType.STRING,100,false,
//                        false,false,true,null,null));
//        Table users = dataBase.openOrCreateTable(schema,databasePath.getNamespaceName());


//        Schema orderSchema = new Schema("orders");
//        orderSchema.addColumn(new ColumnDefinition("id",DataType.INT,null,false,
//                true,false,false,null,null));
//
//        orderSchema.addColumn(new ColumnDefinition("user_id",DataType.INT,null,false,
//                false,true,false,"users","id"));
//        Table orders = dataBase.openOrCreateTable(orderSchema,databasePath.getNamespaceName());

//#########################################################

        System.out.println("================================");
        System.out.println("      TEST WAL CRASH RECOVERY");
        System.out.println("================================");

        Path directory = Path.of("database/test-wal");

        Files.createDirectories(directory);

        Path dataPath = directory.resolve("users.data");
        Path walPath = directory.resolve("users.wal");
        Path fsmPath = directory.resolve("users.fsm");
        Path oldDataPath = directory.resolve("users.data.old");

        /*
         * Limpiamos los archivos de la prueba.
         */
        Files.deleteIfExists(dataPath);
        Files.deleteIfExists(walPath);
        Files.deleteIfExists(fsmPath);
        Files.deleteIfExists(oldDataPath);

        /*
         * Schema.
         */
        Schema schema = new Schema("users");

        schema.addColumn(new ColumnDefinition(
                "id",
                DataType.INT,
                null,
                false,
                true,
                false,
                false,
                null,
                null
        ));

        schema.addColumn(new ColumnDefinition(
                "name",
                DataType.STRING,
                100,
                false,
                false,
                false,
                false,
                null,
                null
        ));

        /*
         * -------------------------------------------------
         * 1. Creamos el estado inicial.
         *
         * Tiene que existir una página válida en .data.
         * -------------------------------------------------
         */

        System.out.println();
        System.out.println("1. Creando estado inicial");

        try (DataFile dataFile =
                     new DataFile(dataPath.toString(), schema)) {

            Record record = new Record(schema);

            record.set(0, 1);
            record.set(1, "Juan");

            dataFile.insert(record);
        }

        System.out.println(
                "Tamaño .data inicial: "
                        + Files.size(dataPath)
        );

        /*
         * Guardamos una copia del estado anterior
         * al segundo INSERT.
         */
        Files.copy(
                dataPath,
                oldDataPath,
                StandardCopyOption.REPLACE_EXISTING
        );

        /*
         * Eliminamos el WAL anterior.
         *
         * Queremos que el siguiente WAL represente
         * únicamente el segundo INSERT.
         */
        Files.deleteIfExists(walPath);

        /*
         * -------------------------------------------------
         * 2. Segundo INSERT
         *
         * Este es el cambio que vamos a simular
         * que quedó a mitad de camino por un crash.
         * -------------------------------------------------
         */

        System.out.println();
        System.out.println("2. Ejecutando segundo INSERT");

        try (DataFile dataFile =
                     new DataFile(dataPath.toString(), schema)) {

            Record record = new Record(schema);

            record.set(0, 2);
            record.set(1, "Pedro");

            dataFile.insert(record);
        }

        System.out.println(
                "Tamaño .data después del INSERT: "
                        + Files.size(dataPath)
        );

        System.out.println(
                "Tamaño WAL: "
                        + Files.size(walPath)
        );

        /*
         * -------------------------------------------------
         * 3. Leemos el WAL.
         * -------------------------------------------------
         */

        List<WalRecord> walRecords;

        try (WalFile walFile =
                     new WalFile(walPath.toString())) {

            walRecords = walFile.readAll();
        }

        if (walRecords.isEmpty()) {
            throw new IllegalStateException(
                    "El WAL no contiene registros"
            );
        }

        WalRecord walRecord = walRecords.getLast();

        byte[] expectedPage = walRecord.getPageData();

        System.out.println(
                "Página en WAL: "
                        + walRecord.getPageId()
        );

        System.out.println(
                "Bytes de página en WAL: "
                        + expectedPage.length
        );

        /*
         * -------------------------------------------------
         * 4. SIMULAMOS CRASH
         *
         * Restauramos .data al estado anterior
         * al segundo INSERT.
         *
         * El WAL permanece intacto.
         * -------------------------------------------------
         */

        System.out.println();
        System.out.println("3. Simulando crash");

        Files.copy(
                oldDataPath,
                dataPath,
                StandardCopyOption.REPLACE_EXISTING
        );

        System.out.println(
                ".data restaurado al estado anterior."
        );

        System.out.println(
                "Tamaño .data: "
                        + Files.size(dataPath)
        );

        System.out.println(
                "Tamaño WAL: "
                        + Files.size(walPath)
        );

        /*
         * -------------------------------------------------
         * 5. REABRIMOS DataFile
         *
         * El constructor debe ejecutar:
         *
         *     WAL Recovery
         *          ↓
         *     restaurar .data
         *          ↓
         *     initializeFreeSpaceMap()
         * -------------------------------------------------
         */

        System.out.println();
        System.out.println("4. Reabriendo DataFile");

        try (DataFile dataFile =
                     new DataFile(dataPath.toString(), schema)) {

            System.out.println(
                    "DataFile reabierto correctamente."
            );
        }

        /*
         * -------------------------------------------------
         * 6. Verificamos que .data coincida con el WAL.
         * -------------------------------------------------
         */

        System.out.println();
        System.out.println("5. Verificando recovery");

        byte[] recoveredData =
                Files.readAllBytes(dataPath);

        System.out.println(
                "Tamaño .data recuperado: "
                        + recoveredData.length
        );

        if (!Arrays.equals(
                recoveredData,
                expectedPage)) {

            throw new AssertionError(
                    "La página recuperada no coincide con el WAL"
            );
        }

        System.out.println(
                "OK: .data fue restaurado desde el WAL."
        );

        System.out.println(
                "OK: La página recuperada coincide con el WAL."
        );

        Files.deleteIfExists(oldDataPath);

        System.out.println();
        System.out.println("================================");
        System.out.println("   TEST WAL CRASH RECOVERY OK");
        System.out.println("================================");




//#########################################################

//        printTable(orders);
//        System.out.println("#############################################################");
//        printTable(users);

        // =========================================================
        // CLOSE
        // =========================================================

//        users.close();
//        orders.close();
//
//        System.out.println();
//        System.out.println("================================");
//        System.out.println("       TEST FINALIZADO");
//        System.out.println("================================");

    }

    private static void printTable(Table table) throws IOException {
        for (int i = 0; i < 8 ; i++) {
            System.out.println(
                    table.getName()+ " actualizado: " + table.find(i)
            );
        }
    }
    private static DataBase abrirDB() throws IOException {
        App.databasePath = new DatabasePath(App.db, App.namespace, App.table);
        DataBase db = DataBase.openOrCreate(App.databasePath.getDatabaseName(), App.databasePath.getNamespaceName(), App.databasePath.getTableName());
        return db;
    }
    private static DataBase crearDB() throws IOException {
        App.databasePath = new DatabasePath(App.db, App.namespace);
        DataBase db = DataBase.openOrCreate(App.databasePath.getDatabaseName(), App.databasePath.getNamespaceName(), null);
        return db;
    }
}
