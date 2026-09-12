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

        Schema schema = new Schema("users"); schema.addColumn(new ColumnDefinition( "id", DataType.INT, null, false, true, false, true, null, null )); schema.addColumn(new ColumnDefinition( "name", DataType.STRING, 50, false, false, false, false, null, null )); schema.addColumn(new ColumnDefinition( "age", DataType.INT, null, true, false, false, false, null, null )); schema.addColumn(new ColumnDefinition( "active", DataType.BOOLEAN, null, false, false, false, false, null, null )); schema.validate();
        Path tablePath = Path.of("database", "users"); Files.createDirectories(tablePath); Path dataPath = tablePath.resolve("data.nst"); Path fsmPath = Path.of(dataPath + ".fsm"); Files.deleteIfExists(dataPath); Files.deleteIfExists(fsmPath); RecordPointer pointer1; RecordPointer pointer2;
        try (DataFile dataFile = new DataFile(dataPath.toString(), schema)) { Record user1 = new Record(schema); user1.set(0, 1); user1.set(1, "Nestor"); user1.set(2, 35); user1.set(3, true); Record user2 = new Record(schema); user2.set(0, 2); user2.set(1, "Juan"); user2.set(2, 30); user2.set(3, false); pointer1 = dataFile.insert(user1); pointer2 = dataFile.insert(user2); System.out.println("=== INSERT ==="); System.out.println("User 1 -> " + pointer1); printRecord(dataFile.read(pointer1)); System.out.println(); System.out.println("User 2 -> " + pointer2); printRecord(dataFile.read(pointer2)); }
        try (DataFile dataFile = new DataFile(dataPath.toString(), schema)) { dataFile.delete(pointer2); System.out.println(); System.out.println("=== DELETE ==="); System.out.println("Eliminado: " + pointer2); System.out.println( "Slot eliminado: " + pointer2.getSlotId() ); }
        try (DataFile dataFile = new DataFile(dataPath.toString(), schema)) { System.out.println(); System.out.println("=== AFTER REOPEN ==="); Record user1 = dataFile.read(pointer1); System.out.println("User 1 sigue existiendo:"); printRecord(user1); try { dataFile.read(pointer2); System.out.println( "ERROR: el registro eliminado todavía puede leerse." ); } catch (IllegalArgumentException e) { System.out.println( "OK: el slot " + pointer2.getSlotId() + " está libre." ); } }
        try (DataFile dataFile = new DataFile(dataPath.toString(), schema)) { Record user3 = new Record(schema); user3.set(0, 3); user3.set(1, "Pedro"); user3.set(2, 28); user3.set(3, true); RecordPointer pointer3 = dataFile.insert(user3); System.out.println(); System.out.println("=== INSERT AFTER DELETE ==="); System.out.println("User 3 -> " + pointer3); printRecord(dataFile.read(pointer3)); System.out.println(); System.out.println( "¿Reutilizó el slot de Juan? " + (pointer3.getSlotId() == pointer2.getSlotId()) ); } }
    private static void printRecord(Record record) { System.out.println("Record:"); for (int i = 0; i < record.getSchema().getColumns().size(); i++) { ColumnDefinition column = record.getSchema().getColumns().get(i); System.out.println( " " + column.getName() + " = " + record.get(i) ); } }
}
