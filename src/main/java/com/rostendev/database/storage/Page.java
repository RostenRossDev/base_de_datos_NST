package com.rostendev.database.storage;

import com.rostendev.database.constants.Constants;

import java.util.ArrayList;
import java.util.List;

public class Page {

    private final int pageId;
    private final List<Slot> slots;
    private final byte[] data;

    private int freeStart;
    private int freeEnd;

    public Page(int pageId){
        if (pageId < 0) throw new IllegalArgumentException("El id de la pagina no puede ser negativo.");
        this.pageId = pageId;
        this.slots = new ArrayList<>();
        this.freeStart = Constants.HEADER_SIZE;
        this.freeEnd = Constants.PAGE_SIZE;
        this.data = new byte[Constants.PAGE_SIZE];
    }

    /*Cantidad de bytes disponibles actualmente entre el slot directory y los registros*/
    public int getFreeSpace() {
        return freeEnd - freeStart;
    }

    /*Inserta un nuevo registro en la pagina
    * @return RecordPoiner indicando donde quedo almacenado.
    * */
    public RecordPointer insert(byte[] recordData) {
        if (recordData == null) throw new IllegalArgumentException("data no puede ser null.");
        if (recordData.length == 0)  throw new IllegalArgumentException("No se puede insertar un registro vacio.");
        /*
         * Necesitamos espacio para:
         *
         * 1. Los bytes del registro
         * 2. Una nueva entrada en el slot directory
         */
        if(!canFit(recordData.length)) throw new IllegalArgumentException("No hay espacio en la pagina.");

        /* El registro se coloca desde el final de la página hacia atrás. */
        int recordOffset = freeEnd - recordData.length;

        /*
         * Copiamos los bytes del registro dentro
         * del espacio físico de la página.
         */
        System.arraycopy(recordData,0,data,recordOffset,recordData.length);

        /*Creamos el slot*/
        short slotId = (short) slots.size();
        Slot slot = new Slot(recordOffset, recordData.length);
        slots.add(slot);

        /*El slot directory crece hacia abajo*/
        freeStart += Constants.SLOT_SIZE;

        /*Los registros crecen hacia arriba*/
        freeEnd = recordOffset;
        return  new RecordPointer(pageId, slotId);
    }

    /**
     * Lee los bytes de un registro utilizando su slot.
     */
    public byte[] read(short slotId) {
        Slot slot = getSlot(slotId);
        if (slot.isFree()) throw new IllegalArgumentException("Ek skit esta libre");
        byte[] result = new byte[slot.getLength()];
        System.arraycopy(data, slot.getOffset(), result, 0, slot.getLength());
        return result;
    }

    /*Obtenemos el slot correspondiente*/
    public Slot getSlot(int slotId) {
        if (slotId < 0 || slotId >= slots.size()) throw new IllegalArgumentException("Slot inexistente: " + slotId);
        return slots.get(slotId);
    }

    /*Indica si existe espacio sufucuetne para un nuevo registro*/
    public boolean canFit(int dataLenth){
        if (dataLenth <= 0) return false;
        return dataLenth + Constants.SLOT_SIZE <= getFreeSpace();
    }

    public int getPageId() {
        return pageId;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public int getFreeStart() {
        return freeStart;
    }

    public int getFreeEnd() {
        return freeEnd;
    }

    public void setSlotInfo(int slotId, int offset, int length) {
        if (slotId < 0) throw new IllegalArgumentException("slotId no puede ser negativo");
        if (slotId >= slots.size()) throw new IllegalArgumentException("slotId inválido: " + slotId);
        Slot slot = slots.get(slotId);
        slot.setOffset(offset);
        slot.setLength(length);
    }

    public void setFreeStart(int freeStart) {
        if (freeStart < Constants.HEADER_SIZE ||freeStart > Constants.PAGE_SIZE)
            throw new IllegalArgumentException("freeStart inválido: " + freeStart);
        this.freeStart = freeStart;
    }

    public void setFreeEnd(int freeEnd) {
        if (freeEnd < Constants.HEADER_SIZE || freeEnd > Constants.PAGE_SIZE)
            throw new IllegalArgumentException("freeEnd inválido: " + freeEnd);
        this.freeEnd = freeEnd;
    }

    /** Reserva un nuevo slot.
    *
    * Se utilizará al deserializar la página.
    */
    public void addSlot(Slot slot) {
        if (slot == null) throw new IllegalArgumentException("slot no puede ser null");
        slots.add(slot);
    }

    /** Marca un registro como eliminado.
     * Por ahora no compactamos la página.
     */
    public void delete(short slotId) {
        Slot slot = getSlot(slotId);
        /*
         * Por ahora no movemos registros.
         * Simplemente marcamos el slot como libre.
         */
        slot.setLength(0);
    }

    public int getSlotCount(){
        return this.slots.size();
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Page{" +
                "pageId=" + pageId +
                ", slots=" + slots +
                ", freeStart=" + freeStart +
                ", freeEnd=" + freeEnd +
                '}';
    }
}
