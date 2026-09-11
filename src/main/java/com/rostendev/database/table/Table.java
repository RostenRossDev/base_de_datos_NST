package com.rostendev.database.table;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.index.BPlusTree;
import com.rostendev.database.index.IndexEntry;
import com.rostendev.database.index.IndexManager;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.schema.SchemaFile;
import com.rostendev.database.storage.DataFile;
import com.rostendev.database.storage.Slot;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceManager;
import com.rostendev.database.storage.Page;
import com.rostendev.database.storage.RecordPointer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class Table {
    private final String name;
    private final Schema schema;
    private final DatabasePath databasePath;
    private final DataFile dataFile;
    private final FreeSpaceManager freeSpaceManager;
    private final BPlusTree index;
    private final int primaryKeyColumn;
    private final IndexManager indexManager;
//    private final Map<String, BPlusTree> uniqueIndexes;

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
        this.indexManager =new IndexManager(databasePath,schema);
    }

    public Table(DatabasePath databasePath, Schema schema) throws IOException {
        if (databasePath == null) throw new IllegalArgumentException("databasePath no puede ser null");
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        this.name = schema.getTableName();
        this.schema = schema;
        this.databasePath = databasePath;
        this.dataFile = new DataFile(databasePath.getDataPath().toString(), schema);
        this.freeSpaceManager = new FreeSpaceManager( dataFile, databasePath.getFsmPath().toString());
        this.primaryKeyColumn = findPrimaryKeyColumn();
        ColumnDefinition primaryKey = schema.getColumns().get(primaryKeyColumn);
        this.index = new BPlusTree(databasePath.getIndexPath(),primaryKey.getType());
        this.indexManager =new IndexManager(databasePath,schema);
    }

    /*Insert*/
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");

        validateRecord(record);
        Object primaryKey = record.get(primaryKeyColumn);
        if (primaryKey == null) throw new IllegalArgumentException("La clave primaria no puede ser null");

        // VALIDAR PRIMARY KEY
        // =====================================================
        IndexEntry existing = index.search(primaryKey);
        if (existing != null) throw new IllegalArgumentException("La clave primaria ya existe");

        // VALIDAR UNIQUE
        // =====================================================
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isUnique() || column.isPrimaryKey()) continue;
            Object value = record.get(i);
            // UNIQUE nullable permite múltiples NULL
            if (value == null) continue;
            IndexEntry uniqueExisting = indexManager.search(i, value);

            if (uniqueExisting != null)
                throw new IllegalArgumentException("La columna '" +column.getName() +
                                "' no permite valores duplicados: " +value);
        }

        // SERIALIZAR
        // =====================================================
        RecordSerializer serializer = new RecordSerializer();
        byte[] recrodData = serializer.serialize(record);

        /*Buscamos una pagina donde pueda entrar*/
        Page page = freeSpaceManager.findPage(recrodData.length);

        /*insertamos el record en la pagina*/
        RecordPointer pointer = page.insert(recrodData);

        // GUARDAR EN DATA
        // =====================================================
        dataFile.write(page);
        System.out.println("FSM -> page=" + page.getPageId()+ " freeSpace=" + page.getFreeSpace()
                        + " insertable=" + page.getInsertableSpace());
        freeSpaceManager.updatePage(page);

        // INSERTAR BTREE PRIMARY KEY
        // =====================================================
        index.insert(primaryKey, pointer.getPageId(), pointer.getSlotId());

        // INSERTAR UNIQUE INDEXES
        // =====================================================
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isUnique() || column.isPrimaryKey()) continue;
            Object value = record.get(i);
            if (value == null) continue;
            indexManager.insert(i,value,pointer);
        }

        return pointer;
    }

    public RecordPointer getPointer(Object primaryKey) throws IOException {
        IndexEntry entry = index.search(primaryKey);
        if (entry == null) return null;
        return new RecordPointer(entry.getPageNumber(),(short) entry.getSlotNumber());
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
        // Recuperamos el registro usando directamente el pointer
        byte[] recordData = page.read(pointer.getSlotId());
        RecordSerializer serializer = new RecordSerializer();
        Record record = serializer.deserialize(recordData, schema);

        // Necesitamos la PK para eliminarla del B+Tree
        Object primaryKey = record.get(primaryKeyColumn);
        // 1. Eliminar del índice
        index.delete(primaryKey);
        // 2. Liberar el slot físicamente
        page.delete(pointer.getSlotId());
        // 3. Persistir la página
        dataFile.write(page);
        // 4. Informar al FSM
        freeSpaceManager.updatePage(page);
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

    public void update(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");

        if (record.getSchema() != schema)
            throw new IllegalArgumentException("El record pertenece a otro schema");
        validateRecord(record);
        Object primaryKey = record.get(primaryKeyColumn);
        if (primaryKey == null)
            throw new IllegalArgumentException("La clave primaria no puede ser null");

        /* Buscamos el registro actual mediante el índice */
        IndexEntry entry = index.search(primaryKey);
        if (entry == null)
            throw new IllegalArgumentException("No existe un registro con la clave primaria: " + primaryKey);

        RecordPointer oldPointer =new RecordPointer(entry.getPageNumber(),(short) entry.getSlotNumber());
        RecordPointer pointer =new RecordPointer(entry.getPageNumber(),(short) entry.getSlotNumber());
        Page oldPage =dataFile.read(oldPointer.getPageId());
        if (oldPage == null) throw new IOException("No existe la pagina: " +oldPointer.getPageId());

        /* Serializamos el nuevo registro */
        RecordSerializer serializer = new RecordSerializer();
        byte[] recordData = serializer.serialize(record);

        Slot oldSlot = oldPage.getSlot(oldPointer.getSlotId());

       /* CASO 1:  El nuevo registro entra en el espacio actual.*/
        if (recordData.length <= oldSlot.getLength()) {
            oldPage.update(oldPointer.getSlotId(),recordData);
            dataFile.write(oldPage);
            freeSpaceManager.updatePage(oldPage);
            return;
        }

        /* CASO 2: El nuevo registro es más grande.
         * Primero eliminamos físicamente el registro viejo.*/
        index.delete(primaryKey);
        oldPage.delete(oldPointer.getSlotId());
        dataFile.write(oldPage);
        freeSpaceManager.updatePage(oldPage);

        /*Insertamos el nuevo registro. findPage() puede reutilizar la misma página o buscar otra.*/
        Page newPage = freeSpaceManager.findPage(recordData.length);
        RecordPointer newPointer =newPage.insert(recordData);
        dataFile.write(newPage);
        freeSpaceManager.updatePage(newPage);

        /*El índice ahora apunta a la nueva ubicación.*/
        index.insert(primaryKey,newPointer.getPageId(),newPointer.getSlotId());
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
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            Object value = record.get(i);
            // NULL
            if (value == null) {
                if (!column.isNullable()) {
                    throw new IllegalArgumentException("La columna '" +column.getName() +"' no puede ser null");
                }
                continue;
            }

            // TYPE
            if (!isValidType(value, column.getType())) {
                throw new IllegalArgumentException("Tipo inválido para la columna '" +column.getName() +
                                "'. Esperado: " +column.getType() +", recibido: " +value.getClass().getSimpleName());
            }

            // STRING LENGTH
            if (column.getType() == DataType.STRING && column.getLength() != null) {

                String stringValue = (String) value;
                if (stringValue.length() > column.getLength()) {
                    throw new IllegalArgumentException("La columna '" +column.getName() +"' permite como máximo " +
                                    column.getLength() +" caracteres");
                }
            }
        }
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

    private boolean isValidType(Object value, DataType type) {

        return switch (type) {
            case BYTE -> value instanceof Byte;
            case SHORT -> value instanceof Short;
            case INT -> value instanceof Integer;
            case LONG -> value instanceof Long;
            case DOUBLE -> value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case BIGDECIMAL -> value instanceof java.math.BigDecimal;
            case BIGINT -> value instanceof java.math.BigInteger;
        };
    }
}
