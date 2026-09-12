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

    /**
     * Inserta un registro en la página.
     * Si existe un slot libre, se reutiliza su slotId.
     * Si no existe, se crea un nuevo slot.
     * @return RecordPointer indicando la ubicación física
     *         del registro.
     */
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
                /* Los registros crecen desde el final de la página hacia freeStart.*/
                int recordOffset = freeEnd - recordData.length;
                System.arraycopy(recordData, 0, data, recordOffset, recordData.length);
                /*Reutilizamos el mismo slot. eL SLOTiD NO CAMBI*/
                slot.setOffset(recordOffset);
                slot.setLength(recordData.length);
                freeEnd = recordOffset;
                return  new RecordPointer(pageId, slotId);
            }
        }

        /* * 2. NO HAY SLOT REUTILIZABLE
        * * ========================================== */
        /*Necesitamos espacio para:
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

    /**
     * Indica si existe espacio suficiente para insertar
     * un registro.
     * Si existe un slot libre solamente necesitamos
     * espacio para los bytes del registro.
     * Si no existe, también necesitamos crear un nuevo slot.
     */
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
        if (slotId >= slots.size()) throw new IllegalArgumentException("slotId invalido: " + slotId);
        if (offset < Constants.HEADER_SIZE || offset > Constants.PAGE_SIZE)
            throw new IllegalArgumentException("offset invalido: " + offset);
        if (length < 0 || offset + length > Constants.PAGE_SIZE)
            throw new IllegalArgumentException("length invalido: " + length);
        Slot slot = slots.get(slotId);
        slot.setOffset(offset);
        slot.setLength(length);
    }

    public void setFreeStart(int freeStart) {
        if (freeStart < Constants.HEADER_SIZE || freeStart > Constants.PAGE_SIZE)
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

    /**Elimina un registro y compacta el área de registros.
     * El slot se conserva para poder reutilizar su slotId
     * en una futura inserción.
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
                if (slot.isFree()) continue;
                if (slot == deletedSlot) continue;
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

    /**
     * Cantidad máxima de bytes de registro que puede
     * recibir actualmente esta página.
     *
     * Si existe un slot libre, no necesitamos crear
     * una nueva entrada en el slot directory.
     */
    public int getInsertableSpace() {
        boolean hasFreeSlot = slots.stream().anyMatch(Slot::isFree);
        if (hasFreeSlot) return getFreeSpace();
        return Math.max(0,getFreeSpace() - Constants.SLOT_SIZE);
    }

    /**
     * Actualiza un registro existente.
     *
     * Este método solamente permite registros del mismo
     * tamaño o más pequeños.
     *
     * Si el registro necesita crecer, DataFile será
     * responsable de moverlo a otra ubicación física.
     */
    public void update(short slotId, byte[] newData) {
        if (newData == null || newData.length == 0)
            throw new IllegalArgumentException("data no puede ser null o vacio");

        Slot slot = slots.get(slotId);

        if (slot == null || slot.isFree())
            throw new IllegalArgumentException("El slot no existe o esta libre: " + slotId);
        if (newData.length > slot.getLength())
            throw new IllegalArgumentException("El nuevo registro es mas grande que el espacio actual");
        int oldOffset = slot.getOffset();
        int oldLength = slot.getLength();
        int newLength = newData.length;

        /* El nuevo registro tiene el mismo tamaño */
        if (newLength == oldLength) {
            System.arraycopy(newData,0,data,oldOffset,newLength);
            return;
        }

        /*==========================================
         * REGISTRO MÁS PEQUEÑO
         * ==========================================
         * Liberamos la diferencia compactando
         * el área de registros. */

        int difference = oldLength - newLength;
        int blockStart = freeEnd;
        int blockLength = oldOffset - blockStart;

        if (blockLength > 0) {
            System.arraycopy(data,blockStart,data,blockStart + difference,blockLength);

            /* Actualizamos los offsets de los registros que fueron desplazados.*/
            for (Slot other : slots) {
                if (other == slot || other.isFree()) continue;
                if (other.getOffset() < oldOffset) other.setOffset(other.getOffset() + difference);
            }
        }

        /*
         * El registro actualizado también cambia
         * de posición porque el bloque inferior
         * fue desplazado.
         */
        int newOffset = oldOffset + difference;
        System.arraycopy(newData,0,data,newOffset,newLength);
        slot.setOffset(newOffset);
        slot.setLength(newLength);
        /* Recuperamos el espacio liberado.*/
        freeEnd += difference;
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
