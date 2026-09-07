package com.rostendev.database;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.DataFile;
import com.rostendev.database.storage.FreeSpaceManager;
import com.rostendev.database.storage.Page;
import com.rostendev.database.storage.RecordPointer;

import java.io.IOException;

public class Table {
    private final String name;
    private final Schema schema;
    private final DatabasePath databasePath;
    private final DataFile dataFile;
    private final FreeSpaceManager freeSpaceManager;

    public Table(String dbName,  String namespace, Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (schema.getTableName() == null || schema.getTableName().isBlank()) throw new IllegalArgumentException("El schema debe tener un nombre");

        this.name = schema.getTableName();
        this.schema = schema;

        /*Ruta Fisica*/
        this.databasePath = new DatabasePath(dbName, namespace, name);
        this.dataFile = new DataFile(databasePath.getDataPath().toString(), schema);

        /*free space*/
        this.freeSpaceManager = new FreeSpaceManager(dataFile);
    }

    /*Insert*/
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");

        /*Serializamos el record*/
        RecordSerializer serializer = new RecordSerializer();
        byte[] recrodData = serializer.serialize(record);

        /*Buscamos una pagina donde pueda entrar*/
        Page page = freeSpaceManager.findPage(recrodData.length);

        /*insertamos el record en la pagina*/
        RecordPointer pointer = page.insert(recrodData);

        /*Persistimos la pagina*/
        dataFile.write(page);
        return pointer;
    }

    /*READ*/
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
