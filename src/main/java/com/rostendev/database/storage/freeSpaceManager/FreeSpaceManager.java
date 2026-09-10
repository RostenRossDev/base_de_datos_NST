package com.rostendev.database.storage.freeSpaceManager;

import com.rostendev.database.storage.DataFile;
import com.rostendev.database.storage.Page;

import java.io.IOException;

public class FreeSpaceManager {
    private final DataFile dataFile;
    private final FreeSpaceMap freeSpaceMap;

    public FreeSpaceManager(DataFile dataFile, String fsmPath) throws IOException {
        if (dataFile == null) throw new IllegalArgumentException("dataFile no puede ser null");
        this.dataFile = dataFile;
        this.freeSpaceMap = new FreeSpaceMap(fsmPath);
    }

    public Page findPage(int recordLength) throws IOException {
        if (recordLength <= 0) throw new IllegalArgumentException("recordLength debe ser mayor a 0");
        int pageId = freeSpaceMap.findPage(recordLength);
        if (pageId == -1) return new Page(dataFile.getPageCount());
        Page page = dataFile.read(pageId);
        if (page == null) throw new IOException("El Free Space Map referencia una página inexistente: "+ pageId);

        /* El FSM nos dice que debería entrar, pero verificamos contra la página real.*/
        if (!page.canFit(recordLength))
            throw new IOException("Inconsistencia entre Free Space Map y Page. pageId=" + pageId);

        return page;
    }

    public void updatePage(Page page) throws IOException {
        if (page == null) throw new IllegalArgumentException("Page no puede ser null");
        System.out.println("FSM UPDATE -> page="+ page.getPageId()+ " free="+ page.getInsertableSpace());
        freeSpaceMap.updatePage(page.getPageId(), page.getInsertableSpace());
    }
}
