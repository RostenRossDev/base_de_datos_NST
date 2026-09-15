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
import com.rostendev.database.transaction.Transaction;
import com.rostendev.database.wal.TransactionType;
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
import java.util.*;


/**
 * Hello world!
 *
 */
public class App {

    static String db ="test-db", namespace ="test", table ="users";
    static DatabasePath databasePath;

    static void main() throws Exception {
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



    test();


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

    public static void test() throws Exception {

    String dataPath =
            "database/test/rollback-new-pages/data.data";

    cleanFiles(dataPath);

    /*
     * ============================================================
     * SCHEMA
     * ============================================================
     */

    Schema schema = new Schema("users");

        schema.addColumn(new ColumnDefinition(
                "id",
                DataType.INT,
                null,
                        false,
                        true,
                        false,
                        true,
                        null,
                        null
    ));

        schema.addColumn(new ColumnDefinition(
                "name",
                DataType.STRING,
                3000,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null
    ));

        schema.validate();

    /*
     * ============================================================
     * TX1
     *
     * Creamos el estado inicial:
     *
     * página 0 -> Nestor
     *
     * ============================================================
     */

    RecordPointer pointerNestor;

        try (DataFile dataFile =
            new DataFile(dataPath, schema);
    Transaction tx1 =
            new Transaction(dataFile)) {

        tx1.begin();

        Record nestor =
                createRecord(schema, 1, "Nestor");

        pointerNestor =
                tx1.insert(nestor);

        System.out.println(
                "Nestor -> " + pointerNestor
        );

        tx1.commit();
    }

    /*
     * ============================================================
     * VERIFICAR ESTADO INICIAL
     * ============================================================
     */

        try (DataFile dataFile =
            new DataFile(dataPath, schema)) {

        int pageCount =
                dataFile.getPageCount();

        System.out.println();
        System.out.println(
                "Páginas después de TX1: "
                        + pageCount
        );

        if (pageCount != 1) {
            throw new AssertionError(
                    "Se esperaba 1 página, pero hay "
                            + pageCount
            );
        }

        Record nestor =
                dataFile.read(pointerNestor);

        if (!nestor.get(0).equals(1)) {
            throw new AssertionError(
                    "Nestor no existe en el estado inicial"
            );
        }

        System.out.println(
                "Estado inicial: OK"
        );
    }

    /*
     * ============================================================
     * TX2
     *
     * Modificamos la página existente y después creamos
     * páginas nuevas.
     * ============================================================
     */

        try (DataFile dataFile =
            new DataFile(dataPath, schema);
    Transaction tx2 =
            new Transaction(dataFile)) {

        tx2.begin();

        /*
         * --------------------------------------------------------
         * UPDATE SOBRE LA PÁGINA EXISTENTE
         * --------------------------------------------------------
         */

        Record nestorModificado =
                createRecord(
                        schema,
                        1,
                        "Nestor MODIFICADO"
                );

        RecordPointer updatedPointer =
                tx2.update(
                        pointerNestor,
                        nestorModificado
                );

        System.out.println();
        System.out.println(
                "UPDATE Nestor -> "
                        + updatedPointer
        );

        /*
         * Verificar que el UPDATE está visible dentro
         * de la transacción.
         */

        Record nestorInsideTx =
                tx2.read(pointerNestor);

        if (!nestorInsideTx.get(0).equals(1)) {
            throw new AssertionError(
                    "ID de Nestor incorrecto dentro de TX2"
            );
        }

        String nestorNameInsideTx =
                (String) nestorInsideTx.get(1);

        if (!nestorNameInsideTx.startsWith(
                "Nestor MODIFICADO")) {

            throw new AssertionError(
                    "Nestor no fue actualizado dentro de TX2"
            );
        }

        System.out.println(
                "Nestor modificado dentro de TX2: OK"
        );

        /*
         * --------------------------------------------------------
         * INSERT 1
         *
         * La página 0 ya está ocupada.
         * Este registro debería crear la página 1.
         * --------------------------------------------------------
         */

        Record juan =
                createRecord(schema, 2, "Juan");

        RecordPointer pointerJuan =
                tx2.insert(juan);

        System.out.println(
                "INSERT Juan -> "
                        + pointerJuan
        );

        /*
         * --------------------------------------------------------
         * INSERT 2
         *
         * Debería crear la página 2.
         * --------------------------------------------------------
         */

        Record pedro =
                createRecord(schema, 3, "Pedro");

        RecordPointer pointerPedro =
                tx2.insert(pedro);

        System.out.println(
                "INSERT Pedro -> "
                        + pointerPedro
        );

        /*
         * --------------------------------------------------------
         * INSERT 3
         *
         * Debería crear la página 3.
         * --------------------------------------------------------
         */

        Record maria =
                createRecord(schema, 4, "Maria");

        RecordPointer pointerMaria =
                tx2.insert(maria);

        System.out.println(
                "INSERT Maria -> "
                        + pointerMaria
        );

        /*
         * ========================================================
         * VERIFICAR ESTADO DENTRO DE TX2
         * ========================================================
         */

        int pageCountInsideTx =
                dataFile.getPageCount();

        System.out.println();
        System.out.println(
                "Páginas dentro de TX2: "
                        + pageCountInsideTx
        );

        if (pageCountInsideTx != 4) {
            throw new AssertionError(
                    "Se esperaban 4 páginas dentro de TX2, pero hay "
                            + pageCountInsideTx
            );
        }

        /*
         * Verificar Juan.
         */

        Record juanInsideTx =
                tx2.read(pointerJuan);

        if (!juanInsideTx.get(0).equals(2)) {
            throw new AssertionError(
                    "Juan no fue insertado correctamente"
            );
        }

        /*
         * Verificar Pedro.
         */

        Record pedroInsideTx =
                tx2.read(pointerPedro);

        if (!pedroInsideTx.get(0).equals(3)) {
            throw new AssertionError(
                    "Pedro no fue insertado correctamente"
            );
        }

        /*
         * Verificar Maria.
         */

        Record mariaInsideTx =
                tx2.read(pointerMaria);

        if (!mariaInsideTx.get(0).equals(4)) {
            throw new AssertionError(
                    "Maria no fue insertada correctamente"
            );
        }

        System.out.println(
                "Páginas nuevas dentro de TX2: OK"
        );

        /*
         * ========================================================
         * ROLLBACK
         * ========================================================
         */

        System.out.println();
        System.out.println(
                "========== ROLLBACK =========="
        );

        tx2.rollback();

        System.out.println(
                "Rollback terminado"
        );

        /*
         * ========================================================
         * VERIFICAR INMEDIATAMENTE DESPUÉS DEL ROLLBACK
         * ========================================================
         */

        int pageCountAfterRollback =
                dataFile.getPageCount();

        System.out.println();
        System.out.println(
                "Páginas después de rollback: "
                        + pageCountAfterRollback
        );

        /*
         * Volvimos de 4 páginas a 1.
         */

        if (pageCountAfterRollback != 1) {
            throw new AssertionError(
                    "Las páginas nuevas no fueron eliminadas "
                            + "por truncate(). Páginas actuales: "
                            + pageCountAfterRollback
            );
        }

        /*
         * --------------------------------------------------------
         * Verificar Nestor
         * --------------------------------------------------------
         */

        Record nestorAfterRollback =
                dataFile.read(pointerNestor);

        if (!nestorAfterRollback.get(0).equals(1)) {
            throw new AssertionError(
                    "Nestor tiene ID incorrecto después de rollback"
            );
        }

        String nestorNameAfterRollback =
                (String) nestorAfterRollback.get(1);

        if (!nestorNameAfterRollback.startsWith(
                "Nestor")) {

            throw new AssertionError(
                    "Nestor no fue restaurado"
            );
        }

        if (nestorNameAfterRollback.startsWith(
                "Nestor MODIFICADO")) {

            throw new AssertionError(
                    "El UPDATE de Nestor sobrevivió al rollback"
            );
        }

        System.out.println(
                "Nestor restaurado: OK"
        );

        /*
         * --------------------------------------------------------
         * Verificar que las páginas nuevas realmente no existen.
         * --------------------------------------------------------
         */

        if (dataFile.read(1) != null) {
            throw new AssertionError(
                    "La página 1 todavía existe después del rollback"
            );
        }

        if (dataFile.read(2) != null) {
            throw new AssertionError(
                    "La página 2 todavía existe después del rollback"
            );
        }

        if (dataFile.read(3) != null) {
            throw new AssertionError(
                    "La página 3 todavía existe después del rollback"
            );
        }

        System.out.println(
                "Páginas nuevas eliminadas: OK"
        );
    }

    /*
     * ============================================================
     * REAPERTURA
     * ============================================================
     *
     * Ahora cerramos completamente el DataFile y lo volvemos
     * a abrir.
     *
     * Esto comprueba que el estado físico también quedó correcto.
     * ============================================================
     */

        try (DataFile dataFile =
            new DataFile(dataPath, schema)) {

        int pageCount =
                dataFile.getPageCount();

        System.out.println();
        System.out.println(
                "Páginas después de reapertura: "
                        + pageCount
        );

        if (pageCount != 1) {
            throw new AssertionError(
                    "Después de reapertura todavía existen "
                            + "páginas creadas por TX2"
            );
        }

        /*
         * Nestor debe seguir restaurado.
         */

        Record nestor =
                dataFile.read(pointerNestor);

        if (!nestor.get(0).equals(1)) {
            throw new AssertionError(
                    "Nestor no existe después de reapertura"
            );
        }

        String nestorName =
                (String) nestor.get(1);

        if (!nestorName.startsWith("Nestor")) {
            throw new AssertionError(
                    "Nestor tiene nombre incorrecto después "
                            + "de reapertura"
            );
        }

        if (nestorName.startsWith(
                "Nestor MODIFICADO")) {

            throw new AssertionError(
                    "El UPDATE sobrevivió después de reapertura"
            );
        }

        /*
         * Las páginas 1, 2 y 3 deben seguir sin existir.
         */

        if (dataFile.read(1) != null) {
            throw new AssertionError(
                    "La página 1 existe después de reapertura"
            );
        }

        if (dataFile.read(2) != null) {
            throw new AssertionError(
                    "La página 2 existe después de reapertura"
            );
        }

        if (dataFile.read(3) != null) {
            throw new AssertionError(
                    "La página 3 existe después de reapertura"
            );
        }

        System.out.println(
                "Estado restaurado después de reapertura: OK"
        );
    }

    /*
     * ============================================================
     * RESULTADO
     * ============================================================
     */

        System.out.println();
        System.out.println(
                "=============================================="
                );
        System.out.println(
                " TEST ROLLBACK + NUEVAS PÁGINAS: OK"
                );
        System.out.println(
                "=============================================="
                );
}

/*
 * ================================================================
 * CREAR RECORD
 * ================================================================
 */

    private static Record createRecord(
            Schema schema,
            int id,
            String name) {

        Record record =
                new Record(schema);

        StringBuilder value =
                new StringBuilder(name);

        while (value.length() < 2500) {
            value.append("x");
        }

        record.set(0, id);
        record.set(1, value.toString());

        return record;
    }

/*
 * ================================================================
 * LIMPIAR ARCHIVOS
 * ================================================================
 */

    private static void cleanFiles(
            String dataPath) {

        String fsmPath;

        if (dataPath.endsWith(".data")) {

            fsmPath =
                    dataPath.substring(
                            0,
                            dataPath.length()
                                    - ".data".length()
                    ) + ".fsm";

        } else {

            fsmPath =
                    dataPath + ".fsm";
        }

        String walPath;

        if (dataPath.endsWith(".data")) {

            walPath =
                    dataPath.substring(
                            0,
                            dataPath.length()
                                    - ".data".length()
                    ) + ".wal";

        } else {

            walPath =
                    dataPath + ".wal";
        }

        deleteIfExists(dataPath);
        deleteIfExists(fsmPath);
        deleteIfExists(walPath);

        File parent =
                new File(dataPath).getParentFile();

        if (parent != null
                && !parent.exists()) {

            if (!parent.mkdirs()
                    && !parent.exists()) {

                throw new RuntimeException(
                        "No se pudo crear el directorio: "
                                + parent
                );
            }
        }
    }

    private static void deleteIfExists(
            String path) {

        File file =
                new File(path);

        if (file.exists()
                && !file.delete()) {

            throw new RuntimeException(
                    "No se pudo eliminar: "
                            + path
            );
        }
    }



    private static byte[] createPage(byte value) {

        byte[] page =
                new byte[Constants.PAGE_SIZE];

        page[0] = value;

        return page;
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
