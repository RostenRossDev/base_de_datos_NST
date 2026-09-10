package com.rostendev.database.storage;

import com.rostendev.database.constants.Constants;

import java.io.IOException;
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
        /* * ==========================================
        * * 1. BUSCAR SLOT LIBRE REUTILIZABLE
        * * ========================================== */

        for (short slotId = 0; slotId < slots.size(); slotId++) {
            Slot slot = slots.get(slotId);
            if (slot.isFree()) {
                if (recordData.length > getFreeSpace()) throw new IllegalArgumentException("No hay espacio en la pagina");
                int recordOffset = freeEnd - recordData.length;
                System.arraycopy(recordData, 0, data, recordOffset, recordData.length);
                /*Reutilizamos el mismo slot. eL SLOTiD NO CAMBI*/
                slot.setOffset(recordOffset);
                slot.setLength(recordData.length);
                freeEnd = recordOffset;
                return  new RecordPointer(pageId, slotId);
            }
        }
        /* * ==========================================
        * * 2. NO HAY SLOT REUTILIZABLE
        * * ========================================== */

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
    public boolean canFit(int dataLength){
        if (dataLength <= 0) return false;
        boolean hasFreeSlot = slots.stream().anyMatch(Slot::isFree);
        if (hasFreeSlot) return dataLength <= getFreeSpace();
        return dataLength + Constants.SLOT_SIZE <= getFreeSpace();
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
        Slot deletedSlot  = getSlot(slotId);
        if (deletedSlot.isFree()) {
            throw new IllegalArgumentException("El slot ya está libre");
        }
        int deletedOffset = deletedSlot.getOffset();
        int deletedLength = deletedSlot.getLength();

        /* Los registros están almacenados desde freeEnd hacia
         * offsets cada vez mayores.
         *
         * El bloque que está "debajo" del registro eliminado
         * se mueve una sola vez para cerrar el espacio.*/
        int blockStart = freeEnd;
        int blockLength = deletedOffset - blockStart;
        if (blockLength > 0) {
            System.arraycopy(data, blockStart, data, blockStart + deletedLength, blockLength);

            /* Todos los registros que estaban por debajo del
             * eliminado cambiaron de offset.*/
            for (Slot slot : slots) {
                if (!slot.isFree() && slot.getOffset() < deletedOffset) {
                    slot.setOffset(slot.getOffset() + deletedLength);
                }
            }
        }
        /*
         * El espacio liberado queda nuevamente al final
         * del área de registros.
         */
        freeEnd += deletedLength;

        /*
         * Conservamos el slot para poder reutilizar su ID.
         */
        deletedSlot.setLength(0);
    }

    public int getSlotCount(){
        return this.slots.size();
    }

    public byte[] getData() {
        return data;
    }

    public int getInsertableSpace() {
        boolean hasFreeSlot = slots.stream().anyMatch(Slot::isFree);
        if (hasFreeSlot) return getFreeSpace();
        return Math.max(0,getFreeSpace() - Constants.SLOT_SIZE);
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
