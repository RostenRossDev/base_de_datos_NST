package com.rostendev.database.storage;

import com.rostendev.database.records.RecordSerializer;

public class RecordPointer {
    private final int pageId;
    private final short slotId;

    public RecordPointer(int pageId, short slotId) {
        if (pageId < 0) throw new IllegalArgumentException("El id de la pagina no puede ser negativo;");
        if (slotId < 0) throw new IllegalArgumentException("El id del slot no puede ser negativo;");

        this.pageId = pageId;
        this.slotId = slotId;
    }

    public short getSlotId() {
        return slotId;
    }

    public int getPageId() {
        return pageId;
    }

    @Override
    public String toString() {
        return "RecordPointer{" +
                "pageId=" + pageId +
                ", slotId=" + slotId +
                '}';
    }
}
