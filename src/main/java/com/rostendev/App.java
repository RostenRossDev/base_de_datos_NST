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
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {

    public static void main(String[] args) throws Exception {

        DataBase db = DataBase.create("universidad");

        Namespace namespace =
                db.createNamespace("public");

        Schema schema =
                new Schema("persona");

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
                        "email",
                        DataType.STRING,
                        150,
                        true,
                        false,
                        false,
                        true
                )
        );

        schema.addColumn(
                new ColumnDefinition(
                        "dni",
                        DataType.STRING,
                        20,
                        false,
                        false,
                        false,
                        true
                )
        );

        Table table =
                namespace.createTable(schema);

        // =====================================================
        // INSERT 1
        // =====================================================

        Record record1 =
                new Record(schema);

        record1.set(0, 1);
        record1.set(1, "Nestor");
        record1.set(2, "nestor@gmail.com");
        record1.set(3, "12345678");

        table.insert(record1);

        System.out.println("INSERT 1 OK");


        // =====================================================
        // INSERT 2
        // Mismo email -> DEBE FALLAR
        // =====================================================

        Record record2 =
                new Record(schema);

        record2.set(0, 2);
        record2.set(1, "Juan");
        record2.set(2, "nestor@gmail.com");
        record2.set(3, "87654321");

        try {

            table.insert(record2);

            System.out.println(
                    "ERROR: se permitió email duplicado"
            );

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "OK: email duplicado rechazado"
            );

            System.out.println(
                    e.getMessage()
            );
        }


        // =====================================================
        // INSERT 3
        // Mismo DNI -> DEBE FALLAR
        // =====================================================

        Record record3 =
                new Record(schema);

        record3.set(0, 3);
        record3.set(1, "Pedro");
        record3.set(2, "pedro@gmail.com");
        record3.set(3, "12345678");

        try {

            table.insert(record3);

            System.out.println(
                    "ERROR: se permitió DNI duplicado"
            );

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "OK: DNI duplicado rechazado"
            );

            System.out.println(
                    e.getMessage()
            );
        }


        // =====================================================
        // INSERT 4
        // Email NULL -> DEBE ENTRAR
        // =====================================================

        Record record4 =
                new Record(schema);

        record4.set(0, 4);
        record4.set(1, "Maria");
        record4.set(2, null);
        record4.set(3, "11111111");

        table.insert(record4);

        System.out.println(
                "INSERT 4 OK - email NULL"
        );


        // =====================================================
        // INSERT 5
        // Otro email NULL -> TAMBIÉN DEBE ENTRAR
        // =====================================================

        Record record5 =
                new Record(schema);

        record5.set(0, 5);
        record5.set(1, "Ana");
        record5.set(2, null);
        record5.set(3, "22222222");

        table.insert(record5);

        System.out.println(
                "INSERT 5 OK - segundo email NULL"
        );


        // =====================================================
        // INSERT 6
        // Todo diferente -> DEBE ENTRAR
        // =====================================================

        Record record6 =
                new Record(schema);

        record6.set(0, 6);
        record6.set(1, "Carlos");
        record6.set(2, "carlos@gmail.com");
        record6.set(3, "33333333");

        table.insert(record6);

        System.out.println(
                "INSERT 6 OK"
        );
    }
}
