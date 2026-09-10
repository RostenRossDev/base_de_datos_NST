package com.rostendev.database.table;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.index.BPlusTree;
import com.rostendev.database.index.IndexEntry;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.schema.SchemaFile;
import com.rostendev.database.storage.DataFile;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceManager;
import com.rostendev.database.storage.Page;
import com.rostendev.database.storage.RecordPointer;

import java.io.IOException;

public class Table {
    private final String name;
    private final Schema schema;
    private final DatabasePath databasePath;
    private final DataFile dataFile;
    private final FreeSpaceManager freeSpaceManager;
    private final BPlusTree index;
    private final int primaryKeyColumn;

    public Table(String dbName,  String namespace, Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (schema.getTableName() == null || schema.getTableName().isBlank()) throw new IllegalArgumentException("El schema debe tener un nombre");

        this.name = schema.getTableName();
        this.schema = schema;

        /*Ruta Fisica*/
        this.databasePath = new DatabasePath(dbName, namespace, name);

        //Crear el archivo de schema y el data file
        SchemaFile schemaFile =new SchemaFile(databasePath.getSchemaPath().toString());
        schemaFile.write(schema);
        this.dataFile = new DataFile(databasePath.getDataPath().toString(), schema);

        /*free space*/
        this.freeSpaceManager = new FreeSpaceManager(dataFile, databasePath.getFsmPath().toString());

        /*PRIMARY KEY =========================  */
        this.primaryKeyColumn =  findPrimaryKeyColumn();

        /* INDEX * =========================*/
        ColumnDefinition primaryKey = schema.getColumns().get(primaryKeyColumn);
        this.index = new BPlusTree(databasePath.getIndexPath(),  primaryKey.getType());
    }

    /*Insert*/
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");

        validateRecord(record);
        Object primaryKey = record.get(primaryKeyColumn);
        if (primaryKey == null) throw new IllegalArgumentException("La clave primaria no puede ser null");
        /*
         * Por ahora BPlusTree trabaja
         * exclusivamente con int.
         */
//        int id = getPrimaryKeyAsInt(primaryKey);
        IndexEntry existing = index.search(primaryKey);
        if (existing != null) throw new IllegalArgumentException("La clave primaria ya existe");

        /*Serializamos el record*/
        RecordSerializer serializer = new RecordSerializer();
        byte[] recrodData = serializer.serialize(record);

        /*Buscamos una pagina donde pueda entrar*/
        Page page = freeSpaceManager.findPage(recrodData.length);

        /*insertamos el record en la pagina*/
        RecordPointer pointer = page.insert(recrodData);

        /*Persistimos la pagina*/
        dataFile.write(page);
        System.out.println("FSM -> page=" + page.getPageId()+ " freeSpace=" + page.getFreeSpace()
                        + " insertable=" + page.getInsertableSpace());
        freeSpaceManager.updatePage(page);
        /* INSERTAR EN B+TREE ========================= */
        index.insert(primaryKey, pointer.getPageId(), pointer.getSlotId());
        return pointer;
    }

    /*FIND*/
    public Record find(Object primaryKey) throws IOException {
        if (primaryKey == null) throw new IllegalArgumentException("La clave primaria no puede ser null");
        /* Primero buscamos en el índice. */
        IndexEntry entry = index.search(primaryKey);
        if (entry == null) return null;

        /*El indice nos devuelve la ubicacion fisica*/
        RecordPointer pointer = new RecordPointer(entry.getPageNumber(), (short) entry.getSlotNumber());
        /* Ahora DataFile busca la página y Page busca el slot. */
        return read(pointer);
    }

    /* READ BY POINTER =========================*/
    public Record read(RecordPointer pointer) throws IOException{
        if (pointer == null) throw new IllegalArgumentException("Poiner no puede ser null");
        Page page = dataFile.read(pointer.getPageId());
        if (page == null) throw new IOException("No existe la pagina: " + pointer.getPageId());
        byte[] recorData = page.read(pointer.getSlotId());
        RecordSerializer serializer = new RecordSerializer();
        return serializer.deserialize(recorData, schema);
    }

    /*DELETE*/
    public void delete(RecordPointer pointer) throws IOException {
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        Page page = dataFile.read(pointer.getPageId());
        if (page == null) throw new IOException("No existe la pagina " + pointer.getPageId());
        page.delete(pointer.getSlotId());
        dataFile.write(page);
    }

    /*DELETE*/
    public void delete(int id) throws IOException {
        /* Buscamos primero el registro en el índice. */
        IndexEntry entry = index.search(id);
        if (entry == null) return;
        RecordPointer pointer = new RecordPointer(entry.getPageNumber(), (short)entry.getSlotNumber());
        /* Marcamos el slot como libre. */
        Page page = dataFile.read(pointer.getPageId());
        if (page == null) throw new IOException("No existe la pagina " + pointer.getPageId());
        page.delete(pointer.getSlotId());
        dataFile.write(page);
    }

    public int findPrimaryKeyColumn() {
        int found = -1;
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (column.isPrimaryKey()) {
                if (found != -1) throw new IllegalArgumentException("La tabla tien mas de uan clave primaria");
                found = i;
            }
        }
        if (found == -1) throw new IllegalArgumentException("La tabla debe tener una clave primaria");
        return found;
    }

    /* PRIMARY KEY → INT =========================*/
    public int getPrimaryKeyAsInt(Object primaryKey) {
        if (!(primaryKey instanceof Integer))
            throw new IllegalArgumentException("El BPlusTree actual requiere una clave primaria INT." +
                    "Tipo recibido: " + primaryKey.getClass().getSimpleName());
        return (Integer) primaryKey;
    }

    /* VALIDATE RECORD ========================= */
    private void validateRecord(Record record){
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");
    }

    public String getName() {
        return name;
    }

    public Schema getSchema() {
        return schema;
    }

    public DatabasePath getDatabasePath() {
        return databasePath;
    }

    public DataFile getDataFile() {
        return dataFile;
    }

    public FreeSpaceManager getFreeSpaceManager() {
        return freeSpaceManager;
    }

    public void close() throws IOException {
        dataFile.close();
    }
}
