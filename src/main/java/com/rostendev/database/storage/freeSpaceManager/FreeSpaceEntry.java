package com.rostendev.database.storage.freeSpaceManager;

public class FreeSpaceEntry {
    private final int pageId;
    private int freeSpace;

    public FreeSpaceEntry(int pageId, int freeSpace) {
        if (pageId < 0) throw new IllegalArgumentException("Page id no puede ser negativo");
        if (freeSpace < 0) throw new IllegalArgumentException("freeSpace no puede ser negativo");
        this.pageId = pageId;
        this.freeSpace = freeSpace;
    }

    public int getPageId() {
        return pageId;
    }

    public int getFreeSpace() {
        return freeSpace;
    }

    public void setFreeSpace(int freeSpace) {
        if (freeSpace < 0) throw new IllegalArgumentException("freeSpace no puede ser negativo");
        this.freeSpace = freeSpace;
    }
}
