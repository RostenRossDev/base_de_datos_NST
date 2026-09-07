package com.rostendev.database.storage;

public class Slot {
    private int offset;
    private int length;
    public Slot(int offset, int length) {
        if (offset < 0) throw new IllegalArgumentException("offset no puede ser negativo");
        if (length < 0) throw new IllegalArgumentException("length no puede ser negativo");
        this.length = length;
        this.offset = offset;
    }

    public boolean isFree(){
        return length == 0;
    }

    //GETERS SETTERS
    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException("offset no puede ser negativo");
        this.offset = offset;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        if (length < 0) throw new IllegalArgumentException("length no puede ser negativo");
        this.length = length;
    }

    @Override
    public String toString() {
        return "Slot{" +
                "offset=" + offset +
                ", length=" + length +
                '}';
    }
}
