package com.rostendev.database.storage;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.freeSpaceManager.FreeSpaceMap;

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

        if (path.endsWith(".data")) {
            fsmPath =
                    path.substring(0, path.length() - ".data".length())
                            + ".fsm";
        } else {
            fsmPath = path + ".fsm";
        }

        this.freeSpaceMap = new FreeSpaceMap(fsmPath);
    }


    /**
     * Agrega un registro al final del archivo.
     *
     * Formato físico:
     *
     * ┌─────────────────────┐
     * │ LENGTH    4 bytes   │
     * ├─────────────────────┤
     * │ RECORD DATA         │
     * │ N bytes             │
     * └─────────────────────┘
     *
     * @return offset donde comienza el registro
     */
    public RecordPointer insert(Record record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El Record pertenece a otro Schema");
        byte[] recordData = recordSerializer.serialize(record);

        /* Buscamos una página existente que pueda almacenar el registro.*/
        Page page = findPageForInsert(recordData);

        boolean newPage = false;
        /*No encontramos ninguna página disponible. Creamos una nueva.*/
        if (page == null) {
            page = new Page(getPageCount());
            newPage = true;
        }

        /*Page se encarga de: reutilizar un slot libre, o crear un slot nuevo.*/
        RecordPointer pointer = page.insert(recordData);

        /* Persistimos inmediatamente la página. */
        write(page);

        int insertableSpace = page.getInsertableSpace();
        if (newPage) freeSpaceMap.addPage(page.getPageId(), insertableSpace);
        else freeSpaceMap.updatePage(page.getPageId(), insertableSpace);
        return pointer;
    }

    public long write(Page page) throws IOException {
        byte[] bytes = pageSerializer.serialize(page);
        if (bytes.length != Constants.PAGE_SIZE)
            throw new IOException("La página debe tener exactamente "+Constants.PAGE_SIZE+" bytes");

        //Nos posicionamos al final del archivo
        long offset = (long) page.getPageId() * Constants.PAGE_SIZE;
        file.seek(offset);
        //despues los datos
        file.write(bytes);
        return offset;
    }

    /*Lee un registro a partir de su offset fisico*/
    public Page read(int pageId) throws  IOException {
        if (pageId < 0)
            throw  new IOException("pageId no puede ser negativo" + pageId);
        long offset = (long) pageId * Constants.PAGE_SIZE;
         /*
         * Si la posición está más allá del archivo,
         * la página todavía no existe.
         **/
        if (offset >= file.length()) return null;
        /*Una pagina debe estar completa.*/
        long remaining = file.length() - offset;
        if (remaining < Constants.PAGE_SIZE) throw  new IOException("La pagina " + pageId + " esta incompleta.");
        file.seek(offset);
        byte[] bytes = new byte[Constants.PAGE_SIZE];
        file.readFully(bytes);
        //Deserializamos usand oe lschema
        return pageSerializer.deserialize(bytes, pageId);
    }

    public Record read(RecordPointer pointer) throws IOException {
        if (pointer == null)throw new IllegalArgumentException("pointer no puede ser null");
        Page page = read(pointer.getPageId());
        if (page == null) throw new IOException("La página no existe: " + pointer.getPageId());
        byte[] recordData = page.read(pointer.getSlotId());
        return recordSerializer.deserialize(recordData, schema);
    }

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

    public void update(RecordPointer pointer, Record record) throws IOException {
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        if (record.getSchema() != schema) throw new IllegalArgumentException("El record pertenece a otro schema");
        Page page = read(pointer.getPageId());
        if (page == null) throw new IllegalArgumentException("La pagina no existe");
        byte[] newData = recordSerializer.serialize(record);
        page.update(pointer.getSlotId(), newData);
        // Persistimos la página modificada.
        write(page);
        freeSpaceMap.updatePage(page.getPageId(), page.getInsertableSpace());
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

    private Page findPageForInsert(byte[] recordData) throws IOException {
        int pageId = freeSpaceMap.findPage(recordData.length);
        if (pageId == -1) return null;
        Page page = read(pageId);
        if (page == null) throw new IOException("El FSM referencia una pagina inexistente: " + pageId);
        /* Validación defensiva.
         * El FSM es una estructura auxiliar.
         * La Page sigue siendo la autoridad real.*/
        if (!page.canFit(recordData.length)) {
            /*El FSM quedó desactualizado. Lo corregimos.*/
            freeSpaceMap.updatePage(pageId, page.getInsertableSpace());
            return  findPageForInsert(recordData);
        }
        return  page;
    }

    /**cierra el archivo**/
    @Override
    public void close() throws IOException {
        try{
            file.close();
        }finally {
            freeSpaceMap.close();
        }
    }
}
