package com.rostendev.database.storage.freeSpaceManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

public class FreeSpaceMap implements AutoCloseable {
    private static final int ENTRY_SIZE = Integer.BYTES;
    private final RandomAccessFile file;

    /*freeSpace -> pageIds
     * Ejemplo:
     * 500  -> [2, 7]
     * 1000 -> [1]
     * 2000 -> [3, 5]*/
    private final TreeMap<Integer, Set<Integer>> pageByFreeSpace = new TreeMap<>();

    public FreeSpaceMap(String path) throws IOException {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Path no puede ser nulo o vacio");
        File filePath = new File(path);
        File parent = filePath.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) throw new IOException("No se pudo crear el directorio " + parent);
        }

        this.file = new RandomAccessFile(path, "rw");
        load();
    }

    private void load() throws IOException {
        pageByFreeSpace.clear();
        long fileLength = file.length();
        if (fileLength % ENTRY_SIZE != 0) throw new IOException("El Free Space Map esta corrupto");
        int pageCount = (int) (fileLength / ENTRY_SIZE);
        for (int pageId = 0; pageId < pageCount; pageId++) {
            int freeSpace = readFreeSpace(pageId);
            if (freeSpace > 0) addToMemoryIndex(pageId, freeSpace);
        }
    }

    public int findPage(int requiredSpace) {
        if (requiredSpace <= 0)
            throw new IllegalArgumentException("requiredSpace debe ser mayor que cero");

        /* Busca el menor freeSpace que sea >= requiredSpace.
         * Ejemplo: necesitamos 800
         * TreeMap:
         * 500
         * 1000  <-- ceilingEntry(800)
         * 2000
         */
        Map.Entry<Integer, Set<Integer>> entry = pageByFreeSpace.ceilingEntry(requiredSpace);
        if (entry == null) return -1;
        return entry.getValue().iterator().next();
    }

    public void updatePage(int pageId, int freeSpace) throws IOException {
        int oldFreeSpace = readFreeSpace(pageId);
        if (oldFreeSpace > 0) removeFromMemoryIndex(pageId, oldFreeSpace);
        writeFreeSpace(pageId, freeSpace);
        if (freeSpace > 0) addToMemoryIndex(pageId, freeSpace);
    }

    public void addPage(int pageId, int freeSpace) throws IOException {
        if (pageId < 0) throw new IllegalArgumentException("pageId no puede ser negativo");
        long expectedOffset = (long) pageId * ENTRY_SIZE;
        if (file.length() < expectedOffset) throw new  IllegalArgumentException("No se puede agregar la " +
                "la pagina " + pageId + " porque existen paginas anteriores sin registrar");

        writeFreeSpace(pageId, freeSpace);
        if (freeSpace > 0) addToMemoryIndex(pageId, freeSpace);
    }

    public int getFreeSpace(int pageId) throws IOException {
        if (pageId < 0) {
            throw new IllegalArgumentException("pageId no pude ser negativo");
        }
        if ((long) pageId * ENTRY_SIZE >= file.length()) return 0;
        return  readFreeSpace(pageId);
    }

    public int size() {
        int size = 0;
        for (Set<Integer> pageIds : pageByFreeSpace.values()){
            size += pageIds.size();
        }
        return size;
    }

    public int getPageCount() {
        try {
            return (int) (file.length() / ENTRY_SIZE);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo obtener la cantidad de páginas del FSM",e);
        }
    }

//    private FreeSpaceEntry findEntry(int pageId) {
//        for (FreeSpaceEntry entry : entries) {
//            if (entry.getPageId() == pageId) return entry;
//        }
//        return null;
//    }

    private void addToMemoryIndex(int pageId, int freeSpace) {
        pageByFreeSpace.computeIfAbsent(freeSpace, key -> new HashSet<>()).add(pageId);
    }

    private void removeFromMemoryIndex(int pageId, int freeSpace) {
        Set<Integer> pageIds = pageByFreeSpace.get(freeSpace);
        if (pageIds == null) return;
        pageIds.remove(pageId);
        if (pageIds.isEmpty()) pageByFreeSpace.remove(freeSpace);
    }

    private int readFreeSpace(int pageId) throws IOException {
        long offset = (long) pageId * ENTRY_SIZE;
        file.seek(offset);
        return file.readInt();
    }

    private void writeFreeSpace(int pageId, int freeSpace) throws IOException{
        if (pageId < 0) throw new IllegalArgumentException("PageId no puede ser negativo");
        if (freeSpace < 0) throw new IllegalArgumentException("freespace no puede ser negativo");
        long offset = (long) pageId * ENTRY_SIZE;
        file.seek(offset);
        file.writeInt(freeSpace);
    }


    @Override
    public void close() throws IOException  {
        file.close();
    }
}
