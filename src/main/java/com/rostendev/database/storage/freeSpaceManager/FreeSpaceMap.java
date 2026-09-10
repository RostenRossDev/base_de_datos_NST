package com.rostendev.database.storage.freeSpaceManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class FreeSpaceMap implements AutoCloseable {
    private static final int ENTRY_SIZE = Integer.BYTES;
    private final RandomAccessFile file;
    private final List<FreeSpaceEntry> entries = new ArrayList<>();

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
        entries.clear();
        long fileLength = file.length();
        if (fileLength % ENTRY_SIZE != 0) throw new IOException("El Free Space Map esta corrupto");
        int pageCount = (int) (fileLength / ENTRY_SIZE);
        for (int pageId = 0; pageId < pageCount; pageId++) {
            int freeSpace = readFreeSpace(pageId);
            if (freeSpace > 0) entries.add(new FreeSpaceEntry(pageId, freeSpace));
        }
    }

    public int findPage(int requiredSpace) {
        if (requiredSpace <= 0)
            throw new IllegalArgumentException("requiredSpace debe ser mayor que cero");

        for (FreeSpaceEntry entry : entries) {
            if (entry.getFreeSpace() >= requiredSpace) return entry.getPageId();
        }
        return -1;
    }

    public void updatePage(int pageId, int freeSpace) throws IOException {
        writeFreeSpace(pageId, freeSpace);
        FreeSpaceEntry entry = findEntry(pageId);
        if (freeSpace <= 0){
            if (entry != null) entries.remove(entry);
            return;
        }

        if (entry == null) entries.add(new FreeSpaceEntry(pageId, freeSpace));
        else entry.setFreeSpace(freeSpace);
    }

    public void addPage(int pageId, int freeSpace) throws IOException {
        writeFreeSpace(pageId, freeSpace);
        if (freeSpace > 0) {
            FreeSpaceEntry entry = findEntry(pageId);
            if (entry == null) entries.add(new FreeSpaceEntry(pageId, freeSpace));
            else entry.setFreeSpace(freeSpace);
        }
    }

    public List<FreeSpaceEntry> getEntries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    private FreeSpaceEntry findEntry(int pageId) {
        for (FreeSpaceEntry entry : entries) {
            if (entry.getPageId() == pageId) return entry;
        }
        return null;
    }

    private int readFreeSpace(int pageId) throws IOException {
        long offset = (long) pageId * ENTRY_SIZE;
        file.seek(offset);
        return file.readInt();
    }

    private void writeFreeSpace(int pageId, int freeSpace) throws IOException{
        long offset = (long) pageId * ENTRY_SIZE;
        file.seek(offset);
        file.writeInt(freeSpace);
    }

    @Override
    public void close() throws IOException  {
        file.close();
    }
}
