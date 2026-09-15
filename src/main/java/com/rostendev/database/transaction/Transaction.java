package com.rostendev.database.transaction;

import com.rostendev.database.records.Record;
import com.rostendev.database.storage.DataFile;
import com.rostendev.database.storage.Page;
import com.rostendev.database.storage.RecordPointer;

import javax.swing.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Transaction implements AutoCloseable{

    private static final AtomicLong NEXT_ID  = new AtomicLong(1);

    private final DataFile dataFile;
    private final long transactionId;

    private boolean active;
    private boolean commited;
    private final long initialFileLength;

    private final Map<Integer, Page> originalPages = new HashMap<>();

    public Transaction(DataFile dataFile) {
        if (dataFile == null) throw new IllegalArgumentException("dataFile no puede ser null");
        this.dataFile = dataFile;
        this.transactionId = NEXT_ID.getAndIncrement();
        this.active = false;
        this.commited = false;
        try {
            this.initialFileLength = dataFile.length();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo obtener el tamaño inicial del DataFile");
        }
    }

    public void begin()  throws IOException {
        if (active) throw new IllegalArgumentException("La transaccion ya esta activa");
        if (commited) throw new IllegalArgumentException("La transaccion ya fue confirmada");
        dataFile.beginTransaction(transactionId);
        active = true;
    }

    public RecordPointer insert(Record record) throws IOException {
        checkActive();
        Page page = dataFile.findPageForInsert(record);
        if (page != null) saveOriginalPage(page.getPageId());
        return dataFile.insert(record, transactionId);
    }

    public Record read(RecordPointer pointer) throws IOException {
        checkActive();
        return dataFile.read(pointer);
    }

    public RecordPointer update(RecordPointer pointer, Record record) throws IOException {
        checkActive();
        saveOriginalPage(pointer.getPageId());
        return dataFile.update(pointer, record, transactionId);
    }

    public void delete(RecordPointer pointer)throws IOException {
        checkActive();
        saveOriginalPage(pointer.getPageId());
        dataFile.delete(pointer, transactionId);
    }

    public void commit() throws IOException {
        checkActive();
        dataFile.commitTransaction(transactionId);
        commited = true;
        active = false;
    }

    public void rollback() throws IOException {
        checkActive();
        // Restauramos las páginas que existían
        // antes de la transacción.
        for (Page page : originalPages.values()) {
            dataFile.restorePage(page);
        }
        // Eliminamos cualquier página física
        // creada durante la transacción.
        dataFile.truncate(initialFileLength);
        originalPages.clear();
        active = false;
    }

    private void checkActive(){
        if (!active) throw new IllegalStateException("La transaccion no esta activa");
    }

    private void saveOriginalPage(int pageId) throws IOException {
        if (originalPages.containsKey(pageId)) return;
        Page page = dataFile.readPage(pageId);
        originalPages.put(pageId, page);
    }

    @Override
    public void close() throws Exception {
       if (active) rollback();
   }

    public boolean isCommited() {
        return commited;
    }

    public boolean isActive() {
        return active;
    }

    public long getTransactionId() {
        return transactionId;
    }
}
