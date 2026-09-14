package com.rostendev.database.wal;

import com.rostendev.database.constants.Constants;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public class WalRecovery {

    public void recover(String dataPath, WalFile walFile) throws IOException {
        if (dataPath == null || dataPath.isBlank()) throw new IllegalArgumentException("datapath no puede sre nulo o vacio");
        if (walFile == null) throw new IllegalArgumentException("WalFile no puede ser null");
        List<WalRecord> records = walFile.readAll();
        try(RandomAccessFile dataFile = new RandomAccessFile(dataPath, "rw")) {
            for (WalRecord recod : records) {
                long offset = (long) recod.getPageId() * Constants.PAGE_SIZE;
                dataFile.seek(offset);
                dataFile.write(recod.getPageData());
                System.out.println("WAL recovery: pagina " + recod.getPageId() + " restaurada.");
            }
            dataFile.getFD().sync();
        }
    }
}
