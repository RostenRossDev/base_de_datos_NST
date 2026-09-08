package com.rostendev.database.index;

public class IndexEntry {
    private final Object key;
    private final int pageNumber;
    private final int slotNumber;

    public IndexEntry(Object key, int pageNumber, int slotNumber) {
        if (key == null) throw new IllegalArgumentException("La clave no puede ser null");
        if (pageNumber < 0 ) throw new IllegalArgumentException("pageNumber no puede ser negativo");
        if (slotNumber < 0) throw new IllegalArgumentException("slotNumber no puede ser negativo");
        this.key = key;
        this.pageNumber = pageNumber;
        this.slotNumber = slotNumber;
    }

    public Object getKey() {
        return key;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    @Override
    public String toString() {
        return "KEY=" + key
                + ", PAGE=" + pageNumber
                + ", SLOT=" + slotNumber;
    }
}
