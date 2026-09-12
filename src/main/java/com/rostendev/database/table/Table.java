package com.rostendev.database.table;

import com.rostendev.database.database.DatabasePath;
import com.rostendev.database.fkIndex.ForeignKeyManager;
import com.rostendev.database.index.BPlusTree;
import com.rostendev.database.index.IndexEntry;
import com.rostendev.database.index.IndexManager;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.schema.SchemaFile;
import com.rostendev.database.storage.*;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class Table {
    private final String name;
    private final Schema schema;
    private final DatabasePath databasePath;
    private final DataFile dataFile;
    private final FreeSpaceManager freeSpaceManager;
    private final BPlusTree index;
    private final int primaryKeyColumn;
    private final IndexManager indexManager;
    private final Namespace namespace;
    private final ForeignKeyManager foreignKeyManager;

//    private final Map<String, BPlusTree> uniqueIndexes;

    public Table(Namespace namespace, String dbName, Schema schema) throws IOException {
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (schema.getTableName() == null || schema.getTableName().isBlank()) throw new IllegalArgumentException("El schema debe tener un nombre");
        if (namespace == null) throw new IllegalArgumentException("namespace no puede ser null");

        this.name = schema.getTableName();
        this.schema = schema;

        /*Ruta Fisica*/
        this.databasePath = new DatabasePath(dbName, namespace.getNamespaceName(), name);

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
        this.namespace = namespace;
        this.foreignKeyManager = new ForeignKeyManager(this);
    }

    public Table(Namespace namespace, DatabasePath databasePath, Schema schema) throws IOException {
        if (databasePath == null) throw new IllegalArgumentException("databasePath no puede ser null");
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        if (namespace == null) throw new IllegalArgumentException("namespace no puede ser null");
        this.name = schema.getTableName();
        this.schema = schema;
        this.databasePath = databasePath;
        this.dataFile = new DataFile(databasePath.getDataPath().toString(), schema);
        this.freeSpaceManager = new FreeSpaceManager( dataFile, databasePath.getFsmPath().toString());
        this.primaryKeyColumn = findPrimaryKeyColumn();
        ColumnDefinition primaryKey = schema.getColumns().get(primaryKeyColumn);
        this.index = new BPlusTree(databasePath.getIndexPath(),primaryKey.getType());
        this.indexManager =new IndexManager(databasePath,schema);
        this.namespace = namespace;
        this.foreignKeyManager = new ForeignKeyManager(this);
    }

    /*Insert*/
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");

        validateRecord(record);
        // VALIDAR FOREIGN KEY
        // =====================================================
        foreignKeyManager.validate(record);

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
//        dataFile.write(page);
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

        // INSERTAR FOREIGN KEY RELATIONS
        // =====================================================
        for (int i = 0; i < schema.getColumns().size(); i++) {
            if (!foreignKeyManager.hasForeignKey(i)) continue;
            Object foreignKey = record.get(i);
            if (foreignKey == null) continue;
            foreignKeyManager.insert(i,foreignKey,primaryKey,pointer);
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
        if (page == null) throw new IOException("No existe la pagina " +pointer.getPageId());
        byte[] recordData =page.read(pointer.getSlotId());
        RecordSerializer serializer =new RecordSerializer();
        Record record =serializer.deserialize(recordData,schema);

        // =====================================================
        // PRIMARY KEY
        // =====================================================
        Object primaryKey = record.get(primaryKeyColumn);

        // VALIDAR FOREIGN KEYS QUE REFERENCIAN
        // ESTE REGISTRO ==========================================
        String primaryKeyName = schema.getColumns().get(primaryKeyColumn).getName();
        if (namespace.hasReferences(schema.getTableName(), primaryKeyName, primaryKey))
            throw new IllegalArgumentException("No se puede eliminar el registro con clave " +
                    "primaria " + primaryKey + " porque existen registros que lo referencian.");

        // =====================================================
        // ELIMINAR PRIMARY KEY DEL ÍNDICE
        // =====================================================
        index.delete(primaryKey);

        // =====================================================
        // ELIMINAR UNIQUE INDEXES
        // =====================================================

        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isUnique() || column.isPrimaryKey()) continue;
            Object value = record.get(i);

            // NULL nunca fue agregado al índice
            if (value == null) continue;
            indexManager.delete(i,value);
        }
        // ELIMINAR RELACIONES FOREIGN KEY
        // DE ESTE REGISTRO SI ESTA TABLA ES HIJA ==========================================
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isForeignKey()) continue;
            Object foreignKey = record.get(i);
            if (foreignKey == null) continue;
            foreignKeyManager.delete(i, foreignKey, primaryKey);
        }

        // =====================================================
        // ELIMINAR REGISTRO FÍSICAMENTE
        // =====================================================
        page.delete(pointer.getSlotId());
//        dataFile.write(page);
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
//        dataFile.write(page);
    }

    public void update(Record record) throws IOException {
        if (record == null) {
            throw new IllegalArgumentException("record no puede ser null");
        }

        if (record.getSchema() != schema) {
            throw new IllegalArgumentException(
                    "El schema del record no coincide con el schema de la tabla"
            );
        }

        validateRecord(record);
        Object primaryKey = record.get(primaryKeyColumn);
        IndexEntry entry = index.search(primaryKey);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No existe un registro con clave primaria: " + primaryKey
            );
        }

        RecordPointer oldPointer = new RecordPointer(entry.getPageNumber(),(short) entry.getSlotNumber());
        Page oldPage = dataFile.read(oldPointer.getPageId());
        if (oldPage == null)
            throw new IOException("No existe la pagina " + oldPointer.getPageId());

        byte[] oldRecordData = oldPage.read(oldPointer.getSlotId());
        RecordSerializer serializer = new RecordSerializer();
        Record oldRecord = serializer.deserialize(oldRecordData,schema);

        /* La PK es inmutable.
         * Como el registro fue localizado utilizando la nueva PK,
         * si llegamos hasta acá significa que estamos actualizando
         * el mismo registro. */

        /* Validamos UNIQUE antes de modificar nada. */
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isUnique() || column.isPrimaryKey()) continue;
            Object oldValue = oldRecord.get(i);
            Object newValue = record.get(i);
            if (Objects.equals(oldValue, newValue)) continue;
            if (newValue == null) continue;
            IndexEntry uniqueEntry = indexManager.search(i, newValue);

            if (uniqueEntry != null) {
                throw new IllegalArgumentException(
                        "El valor '" + newValue
                                + "' ya existe en la columna UNIQUE '"+ column.getName() + "'"
                );
            }
        }

        /*Validamos FOREIGN KEY antes de modificar nada.
         * Si el nuevo FK apunta a un padre inexistente,
         * el update completo se rechaza. */
        foreignKeyManager.validate(record);
        byte[] newRecordData = serializer.serialize(record);
        boolean sameSize = newRecordData.length <= oldRecordData.length;

        /* Eliminamos temporalmente las relaciones FK antiguas.
         * Esto se hace siempre porque incluso si el FK no cambia,
         * el RecordPointer puede cambiar si el registro debe moverse. */
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isForeignKey()) continue;
            Object oldForeignKey = oldRecord.get(i);
            if (oldForeignKey == null) continue;
            foreignKeyManager.delete(i,oldForeignKey,primaryKey);
        }

        RecordPointer newPointer;
        if (sameSize) {
            /* El registro entra en el slot actual. */
            oldPage.update(oldPointer.getSlotId(),newRecordData);
//            dataFile.write(oldPage);
            freeSpaceManager.updatePage(oldPage);
            newPointer = oldPointer;
        } else {
            /* El registro ya no entra en el slot actual.
             * Eliminamos físicamente el registro viejo y lo insertamos nuevamente. */
            oldPage.delete(oldPointer.getSlotId());
//            dataFile.write(oldPage);
            freeSpaceManager.updatePage(oldPage);
            Page page = freeSpaceManager.findPage(newRecordData.length);
            newPointer = page.insert(newRecordData);
//            dataFile.write(page);
            freeSpaceManager.updatePage(page);
        }

        /*
         * Actualizamos índices UNIQUE.
         */
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isUnique() || column.isPrimaryKey()) continue;
            Object oldValue = oldRecord.get(i);
            Object newValue = record.get(i);
            if (Objects.equals(oldValue, newValue)) continue;
            if (oldValue != null) indexManager.delete(i, oldValue);
            if (newValue != null) indexManager.insert(i,newValue,newPointer);
        }

        /*
         * Si el registro cambió de posición, el índice PK debe
         * apuntar al nuevo RecordPointer.
         */
        if (!oldPointer.equals(newPointer)) {
            index.delete(primaryKey);
            index.insert(primaryKey,newPointer.getPageId(),newPointer.getSlotId());
        }

        /*Creamos nuevamente las relaciones FOREIGN KEY,
         * ahora apuntando al nuevo RecordPointer.*/
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isForeignKey()) continue;
            Object newForeignKey = record.get(i);
            if (newForeignKey == null) continue;
            foreignKeyManager.insert(i,newForeignKey,primaryKey,newPointer);
        }
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

    public Table getReferencedTable(ColumnDefinition column) {
        if (!column.isForeignKey()) throw new IllegalArgumentException("La columa no es una FOREING KEY");
        Table table = namespace.getTable(column.getReferencedTable());
        if (table == null) throw new IllegalArgumentException("No existe la tabla referencia: " + column.getReferencedTable());
        return table;
    }

    public int getColumnIndex(String columnName) {
        for (int i = 0; i < schema.getColumns().size(); i++) {
            if (schema.getColumns().get(i).getName().equals(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No existe la columna: " + columnName);
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

    public boolean hasForeignKeyReference(String referencedTable, String referencedColumn, Object value) throws IOException {
        for (int i = 0; i < schema.getColumns().size(); i++) {
            ColumnDefinition column = schema.getColumns().get(i);
            if (!column.isForeignKey()) continue;
            if (!column.getReferencedTable().equals(referencedTable)) continue;
            if (!column.getReferencedColumn().equals(referencedColumn)) continue;
            if (foreignKeyManager.hasReferences(i, value)) return true;
        }
        return false;
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
