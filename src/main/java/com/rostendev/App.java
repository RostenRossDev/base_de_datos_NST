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

import java.nio.charset.StandardCharsets;
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

        DataBase dataBase = abrirDB();
        Schema schema = new Schema(table);
        Namespace namespace = new Namespace(databasePath, databasePath.getNamespaceName());
        schema.addColumn(new ColumnDefinition("id",DataType.INT,null,false,true,
                        false,false,null,null));
        schema.addColumn(new ColumnDefinition("name",DataType.STRING,100,false,
                        false,false,false,null,null));
        schema.addColumn(new ColumnDefinition("email",DataType.STRING,100,false,
                        false,false,true,null,null));
        Table users = dataBase.openOrCreateTable(schema,databasePath.getNamespaceName());


        Schema orderSchema = new Schema("orders");
        orderSchema.addColumn(new ColumnDefinition("id",DataType.INT,null,false,
                true,false,false,null,null));

        orderSchema.addColumn(new ColumnDefinition("user_id",DataType.INT,null,false,
                false,true,false,"users","id"));
        Table orders = dataBase.openOrCreateTable(orderSchema,databasePath.getNamespaceName());




        PageSerializer serializer = new PageSerializer();

        Page page = new Page(0);

        page.insert("Registro 1".getBytes(StandardCharsets.UTF_8));
        page.insert("Registro 2".getBytes(StandardCharsets.UTF_8));

        byte[] serialized = serializer.serialize(page);

// El primer slot empieza en el byte 8.
// Cada slot ocupa 8 bytes:
//
// 8-11   offset
// 12-15  length
//
// 16-19  offset del segundo slot
// 20-23  length del segundo slot

// Hacemos que el segundo slot apunte
// exactamente al mismo lugar que el primero.
        serialized[16] = serialized[8];
        serialized[17] = serialized[9];
        serialized[18] = serialized[10];
        serialized[19] = serialized[11];

        try {
            Page corrupted = serializer.deserialize(serialized, 0);

            System.out.println("ERROR: se aceptó una página corrupta.");

            System.out.println("Slot 0: offset="
                    + corrupted.getSlot(0).getOffset()
                    + ", length="
                    + corrupted.getSlot(0).getLength());

            System.out.println("Slot 1: offset="
                    + corrupted.getSlot(1).getOffset()
                    + ", length="
                    + corrupted.getSlot(1).getLength());

        } catch (IOException e) {

            System.out.println("OK: se detectó la corrupción.");
            System.out.println("Mensaje: " + e.getMessage());
        }




        printTable(orders);
        System.out.println("#############################################################");
        printTable(users);

        // =========================================================
        // CLOSE
        // =========================================================

        users.close();
        orders.close();

        System.out.println();
        System.out.println("================================");
        System.out.println("       TEST FINALIZADO");
        System.out.println("================================");

    }

    private static void printTable(Table table) throws IOException {
        for (int i = 0; i < 8 ; i++) {
            System.out.println(
                    table.getName()+ " actualizado: " + table.find(i)
            );
        }
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
