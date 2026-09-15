package com.rostendev.database.wal;

import com.rostendev.database.constants.Constants;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

public class WalRecovery {

    public void recover(String dataPath, WalFile walFile) throws IOException {
        if (dataPath == null || dataPath.isBlank()) throw new IllegalArgumentException("datapath no puede sre nulo o vacio");
        if (walFile == null) throw new IllegalArgumentException("WalFile no puede ser null");
        List<WalRecord> records = walFile.readAll();

        /* 1. Determinar qué transacciones llegaron a COMMIT.
         */
        Set<Long> committedTransactions = new HashSet<>();
        for (WalRecord record : records) {
            if (record.getType() == TransactionType.COMMIT) committedTransactions.add(record.getTransactionId());
        }

        /*2. De las páginas pertenecientes a transacciones
         *    confirmadas, conservar solamente la última imagen */
        Map<Integer, WalRecord> latesRecords = new HashMap<>();

        for (WalRecord recod : records) {
            if (recod.getType() != TransactionType.PAGE) continue;
            if (!committedTransactions.contains(recod.getTransactionId())) continue;
            latesRecords.put(recod.getPageId(), recod);
        }

        /* 3. Aplicar las últimas imágenes confirmadas.*/
        try(RandomAccessFile dataFile = new RandomAccessFile(dataPath, "rw")) {
            for (WalRecord recod : latesRecords.values()) {
                long offset = (long) recod.getPageId() * Constants.PAGE_SIZE;
                dataFile.seek(offset);
                dataFile.write(recod.getPageData());
                System.out.println("WAL recovery: pagina " + recod.getPageId() + " restaurada.");
            }
            dataFile.getFD().sync();
        }
    }
}
