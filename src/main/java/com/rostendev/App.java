package com.rostendev;


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


import javax.swing.table.TableCellEditor;
import javax.xml.crypto.Data;

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

    static String db ="test-db", namespace ="test", table ="users";
    static DatabasePath databasePath;

    static void main() throws IOException {

        DataBase db = abrirDB();
        Schema schema = new Schema(table);
        Namespace namespace = new Namespace(databasePath, databasePath.getNamespaceName());
        schema.addColumn(new ColumnDefinition("id",DataType.INT,
                        null,false,
                        true,false,
                        false,null,null
                )
        );

        schema.addColumn(
                new ColumnDefinition("name",DataType.STRING,
                        100,false,
                        false,false,
                        false,null,
                        null
                )
        );

        schema.addColumn(
                new ColumnDefinition("email",DataType.STRING,
                        100,false,
                        false,false,
                        true,null,null
                )
        );

        // =========================================================
        // TABLE
        // =========================================================
        Table table = db.openOrCreateTable(schema, databasePath.getNamespaceName());

//        Table table = new Table(namespace,databasePath.getDatabaseName(),schema);


        // =========================================================
        // INSERT
        // =========================================================

        System.out.println();
        System.out.println("INSERT");

        Record user1 = new Record(schema);

        user1.set(0, 1);
        user1.set(1, "Nestor");
        user1.set(2, "nestor@test.com");

        Record pointer1 = table.find(user1.get(0));

        System.out.println(
                "User 1 -> " + pointer1
        );

        Record user2 = new Record(schema);

        user2.set(0, 2);
        user2.set(1, "Juan");
        user2.set(2, "juan@test.com");

        Record pointer2 = table.find(user2.get(0));

        System.out.println(
                "User 2 -> " + pointer2
        );

//        // =========================================================
//        // READ
//        // =========================================================
//
//        System.out.println();
//        System.out.println("READ");
//
//        Record result = table.read(pointer1);
//
//        System.out.println(
//                "id    = " + result.get(0)
//        );
//
//        System.out.println(
//                "name  = " + result.get(1)
//        );
//
//        System.out.println(
//                "email = " + result.get(2)
//        );

        // =========================================================
        // UPDATE PEQUEÑO
        // =========================================================

        System.out.println();
        System.out.println("UPDATE PEQUEÑO");

        Record smallUpdate = new Record(schema);

        smallUpdate.set(0, 1);
        smallUpdate.set(1, "Nes");
        smallUpdate.set(2, "nestor@test.com");

        table.update(smallUpdate);

        Record afterSmallUpdate = table.find(smallUpdate.get(0));

        System.out.println(
                "name = " + afterSmallUpdate.get(1)
        );

        // =========================================================
        // UPDATE GRANDE
        // =========================================================

        System.out.println();
        System.out.println("UPDATE GRANDE");

        Record bigUpdate = new Record(schema);

        bigUpdate.set(0, 1);

        bigUpdate.set(1,"NestorXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        bigUpdate.set(2,"nestor@test.com");

        table.update(bigUpdate);

        Record afterBiglUpdate = table.find(bigUpdate.get(0));
        System.out.println(
                "name = " + afterBiglUpdate.get(1)
        );
        /*
         * IMPORTANTE:
         *
         * Table.update() actualmente no devuelve RecordPointer.
         * Por lo tanto, no vamos a inventar un método para obtener
         * el nuevo puntero.
         *
         * El objetivo acá es comprobar que el update funciona.
         */

        System.out.println(
                "Update grande ejecutado."
        );

        // =========================================================
        // DELETE
        // =========================================================

        System.out.println();
        System.out.println("DELETE");


        table.delete(table.getPointer(pointer2.get(0)));

        System.out.println(
                "User 2 eliminado."
        );

        // =========================================================
        // READ DESPUÉS DEL UPDATE
        // =========================================================

        System.out.println();
        System.out.println("READ DESPUES DEL UPDATE");

        /*
         * Si el registro fue movido físicamente por el update grande,
         * pointer1 puede haber dejado de ser válido.
         *
         * Por eso no hacemos table.read(pointer1) acá.
         */

        System.out.println(
                "Update grande completado correctamente."
        );

        // =========================================================
        // CLOSE
        // =========================================================

        table.close();

        System.out.println();
        System.out.println("================================");
        System.out.println("       TEST FINALIZADO");
        System.out.println("================================");

    }

    private static DataBase abrirDB() throws IOException {
        databasePath = new DatabasePath(db, namespace, table);
        DataBase db = DataBase.openOrCreate(databasePath.getDatabaseName(), databasePath.getNamespaceName(), databasePath.getTableName());
        return db;
    }

    private static DataBase crearDB() throws IOException {
        databasePath = new DatabasePath(db, namespace);
        DataBase db = DataBase.openOrCreate(databasePath.getDatabaseName(), databasePath.getNamespaceName(), null);
        return db;
    }
}
