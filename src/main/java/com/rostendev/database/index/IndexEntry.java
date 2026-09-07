package com.rostendev.database.index;

public class IndexEntry {
    private final int id;
    private final int pageNumber;
    private final int slotNumber;

    public IndexEntry(int id, int pageNumber, int slotNumber) {
        this.id = id;
        this.pageNumber = pageNumber;
        this.slotNumber = slotNumber;
    }

    public int getId() {
        return id;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    @Override
    public String toString() {
        return "ID=" + id
                + ", PAGE=" + pageNumber
                + ", SLOT=" + slotNumber;
    }
}
