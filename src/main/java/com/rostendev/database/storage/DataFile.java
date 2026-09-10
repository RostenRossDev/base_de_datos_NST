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

        /*No encontramos ninguna página disponible. Creamos una nueva.*/
        if (page == null) page = new Page(getPageCount());


        /*Page se encarga de: reutilizar un slot libre, o crear un slot nuevo.*/
        RecordPointer pointer = page.insert(recordData);

        /* Persistimos inmediatamente la página. */
        write(page);
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
        int pageCount = getPageCount();
        for (int pageId = 0; pageId < pageCount; pageId++) {
            Page page = read(pageId);
            if (page.canFit(recordData.length)) return page;
        }
        return null;
    }

    /**cierra el archivo**/
    @Override
    public void close() throws IOException {
        file.close();
    }

}
