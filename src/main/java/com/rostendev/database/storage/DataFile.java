package com.rostendev.database.storage;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceMap;
import com.rostendev.database.wal.WalFile;
import com.rostendev.database.wal.WalRecord;
import com.rostendev.database.wal.WalRecovery;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DataFile implements Closeable {
    private final RandomAccessFile file;
    private final PageSerializer pageSerializer;
    private final Schema schema;
    private final RecordSerializer recordSerializer;

    private final FreeSpaceMap freeSpaceMap;

    private final WalFile walFile;
    private final WalRecovery walRecovery;

    public DataFile(String path, Schema schema) throws IOException {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Path no puede ser nulo o vacio.");
        if (schema == null) throw new IllegalArgumentException("schema no puede ser null");
        File filePath = new File(path);
        File parent = filePath.getParentFile();
        if (parent != null && !parent.exists())
            if (!parent.mkdirs() && !parent.exists()) throw new IOException("No se pudo crear el directorio " + path);

        this.file = new RandomAccessFile(path, "rw");
        this.pageSerializer = new PageSerializer();
        this.schema = schema;
        this.recordSerializer = new RecordSerializer();

        String fsmPath;
        if (path.endsWith(".data"))
            fsmPath =path.substring(0, path.length() - ".data".length())+ ".fsm";
        else fsmPath = path + ".fsm";
        this.freeSpaceMap = new FreeSpaceMap(fsmPath);
        this.walFile = new WalFile(getWalPath(path));
        this.walRecovery = new WalRecovery();
        walRecovery.recover(path, walFile);
        initializeFreeSpaceMap();
    }


    /**
     * Inserta un registro físicamente.
     *
     * DataFile se encarga de:
     *
     * - serializar el Record
     * - buscar una página con espacio
     * - crear una página si es necesario
     * - insertar el registro en la Page
     * - persistir la Page
     * - actualizar el FreeSpaceMap
     *
     * @return ubicación física del registro.
     */
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El Record pertenece a otro Schema");
        byte[] recordData = recordSerializer.serialize(record);

        /*
         * Buscamos una página existente que pueda
         * almacenar el registro.
         */
        Page page = findPageForInsert(recordData);

        boolean newPage = false;
        /*
         * Si no existe ninguna página disponible,
         * creamos una nueva.
         */
        if (page == null) {
            page = new Page(getPageCount());
            newPage = true;
        }

        /*
         * Page se encarga de:
         *
         * - reutilizar un slot libre
         * - crear un slot nuevo
         * - colocar físicamente los bytes
         */
        RecordPointer pointer = page.insert(recordData);

        /* Persistimos inmediatamente la página. */
        write(page);

        /*
         * Actualizamos el FreeSpaceMap después
         * de persistir correctamente la página.
         */
        int insertableSpace = page.getInsertableSpace();
        if (newPage) freeSpaceMap.addPage(page.getPageId(), insertableSpace);
        else freeSpaceMap.updatePage(page.getPageId(), insertableSpace);
        return pointer;
    }

    /**
     * Persiste una página en su posición física.
     *
     * Este método pertenece exclusivamente a DataFile.
     */
    private  long write(Page page) throws IOException {
        if (page == null) throw new IllegalArgumentException("page no puede ser null");
        byte[] bytes = pageSerializer.serialize(page);
        if (bytes.length != Constants.PAGE_SIZE)
            throw new IOException("La página debe tener exactamente "+Constants.PAGE_SIZE+" bytes");

        //Nos posicionamos al final del archivo
        long offset = (long) page.getPageId() * Constants.PAGE_SIZE;
        persistWal(page, bytes);
        file.seek(offset);
        //despues los datos
        file.write(bytes);
        return offset;
    }

    /**
     * Lee una página física.
     */
    public Page read(int pageId) throws  IOException {
        if (pageId < 0)
            throw  new IOException("pageId no puede ser negativo" + pageId);
        long offset = (long) pageId * Constants.PAGE_SIZE;
        /*
         * La página todavía no existe.
         */
        if (offset >= file.length()) return null;
        /*Una pagina debe estar completa.*/
        long remaining = file.length() - offset;
        /*
         * Una página nunca puede estar parcialmente
         * almacenada.
         */
        if (remaining < Constants.PAGE_SIZE) throw  new IOException("La pagina " + pageId + " esta incompleta.");
        file.seek(offset);
        byte[] bytes = new byte[Constants.PAGE_SIZE];
        file.readFully(bytes);
        //Deserializamos usand oe lschema
        return pageSerializer.deserialize(bytes, pageId);
    }

    /**
     * Lee un Record utilizando su ubicación física.
     */
    public Record read(RecordPointer pointer) throws IOException {
        if (pointer == null)throw new IllegalArgumentException("pointer no puede ser null");
        Page page = read(pointer.getPageId());
        if (page == null) throw new IOException("La página no existe: " + pointer.getPageId());
        byte[] recordData = page.read(pointer.getSlotId());
        return recordSerializer.deserialize(recordData, schema);
    }

    /**
     * Elimina físicamente un registro.
     *
     * Page se encarga de compactar la página.
     * DataFile se encarga de persistirla y actualizar
     * el FreeSpaceMap.
     */
    public void delete(RecordPointer pointer) throws IOException {
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        Page page = read(pointer.getPageId());
        if (page == null) throw new IOException("La pagina no existe: " + pointer.getPageId());
        // Page.delete() se encarga de:
        // - compactar el área de registros
        // - actualizar offsets
        // - liberar el slot
        page.delete(pointer.getSlotId());
        // Persistimos la página modificada.
        write(page);
        freeSpaceMap.updatePage(page.getPageId(), page.getInsertableSpace());
    }

    /**
     * Actualiza físicamente un registro.
     *
     * Actualmente Page.update() solamente permite
     * registros del mismo tamaño o más pequeños.
     *
     * Si el nuevo registro es más grande, todavía
     * debemos implementar aquí el movimiento hacia
     * otra página.
     */
    public RecordPointer  update(RecordPointer pointer, Record record) throws IOException {
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");
        Page oldPage  = read(pointer.getPageId());
        if (oldPage  == null) throw new IllegalArgumentException("La pagina no existe");
        byte[] newData = recordSerializer.serialize(record);
        byte[] oldData = oldPage.read(pointer.getSlotId());
        // =========================================================
        // MISMO TAMAÑO O MÁS PEQUEÑO
        // =========================================================
        if (newData.length <= oldData.length) {
            oldPage.update(pointer.getSlotId(),newData);
            write(oldPage);
            freeSpaceMap.updatePage(oldPage.getPageId(),oldPage.getInsertableSpace());
            return pointer;
        }

        // REGISTRO MÁS GRANDE
        // =========================================================
        /* El registro ya no entra en su espacio actual.
         * Primero liberamos el registro viejo.*/
        oldPage .delete(pointer.getSlotId());

        // Persistimos la página modificada.
        write(oldPage );
        freeSpaceMap.updatePage(oldPage .getPageId(), oldPage .getInsertableSpace());

        /* Buscamos una página donde entre el nuevo registro.*/
        Page newPage = findPageForInsert(newData);
        boolean newPhysicalPage = false;

        if (newPage == null) {
            newPage = new Page(getPageCount());
            newPhysicalPage = true;
        }

        /* Insertamos el registro y conservamos el nuevo RecordPointer.*/
        RecordPointer newPointer = newPage.insert(newData);
        write(newPage);
        if (newPhysicalPage) freeSpaceMap.addPage(newPage.getPageId(),newPage.getInsertableSpace());
        else freeSpaceMap.updatePage(newPage.getPageId(),newPage.getInsertableSpace());
        return newPointer;
    }

    /**
     * Indica cuántas páginas físicas existen
     * actualmente en el archivo.
     */
    public int getPageCount() throws  IOException {
        long length = file.length();

        if (length == 0) return  0;

        if (length % Constants.PAGE_SIZE != 0) throw new IOException("El tamaño del archivo no es multiplo de PAGE_SIZE");
        return (int) length / Constants.PAGE_SIZE;
    }

    /**
     * Obtiene el tamaño físico del archivo.
     */
    public long length() throws IOException {
        return file.length();
    }

    /**
     * Busca una página que pueda almacenar el registro.
     *
     * El FreeSpaceMap se utiliza como índice rápido.
     * La Page sigue siendo la autoridad real.
     */
    private Page findPageForInsert(byte[] recordData) throws IOException {
        while (true) {
            int pageId = freeSpaceMap.findPage(recordData.length);
            /*
             * No existe ninguna página candidata.
             */
            if (pageId == -1) return null;
            Page page = read(pageId);
            if (page == null) throw new IOException("El FSM referencia una pagina inexistente: " + pageId);
            /* Validación defensiva.
             * El FSM es una estructura auxiliar.
             * La Page sigue siendo la autoridad real.*/
            if (page.canFit(recordData.length)) {
                return page;
            }
            /*
             * El FSM estaba desactualizado.
             * Lo corregimos y volvemos a buscar.
             */
            freeSpaceMap.updatePage(pageId,page.getInsertableSpace());
        }
    }

    /**
     * Sincroniza las entradas faltantes del FSM
     * con las páginas existentes en data.nst.
     */
    private void initializeFreeSpaceMap() throws IOException {
        int dataPageCount = getPageCount();
        int fsmPageCount = freeSpaceMap.getPageCount();
        if (fsmPageCount > dataPageCount) throw new IOException("El FSM contiene mas paginas que data.nst");
        for (int pageId = fsmPageCount; pageId < dataPageCount; pageId++) {
            Page page = read(pageId);
            if (page == null) throw new IOException("No se pudo leer la pagina " + pageId);
            freeSpaceMap.addPage(pageId, page.getInsertableSpace());
        }
    }

    private void persistWal(Page page, byte[] pageData) throws IOException {
        WalRecord walRecord = new WalRecord(page.getPageId(), pageData);
        walFile.append(walRecord);
        walFile.flush();
    }

    private String getWalPath(String path) throws IOException {
        String walPath;
        if (path.endsWith(".data")) walPath = path.substring(0, path.length() - ".data".length()) + ".wal";
        else walPath  = path + ".wal";
        return walPath;
    }




    /**cierra el archivo**/
    @Override
    public void close() throws IOException {
        try{
            file.close();
        }finally {
            freeSpaceMap.close();
            walFile.close();
        }
    }
}
