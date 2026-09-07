package com.rostendev.database.storage;

import java.io.IOException;

public class FreeSpaceManager {
    private final DataFile dataFile;

    public FreeSpaceManager(DataFile dataFile) {
        if (dataFile == null) throw new IllegalArgumentException("dataFile no puede ser null");
        this.dataFile = dataFile;
    }

    public Page findPage(int recordLength) throws IOException {
        if (recordLength <= 0) throw new IllegalArgumentException("recordLength debe ser mayor a 0");
        int pageCount = dataFile.getPageCount();
        for (int pageId  = 0; pageId  < pageCount; pageId ++) {
            Page page = dataFile.read(pageId);
            if (page == null) continue;
            if (page.canFit(recordLength)) return page;
        }
        return new Page(pageCount);
    }
}
