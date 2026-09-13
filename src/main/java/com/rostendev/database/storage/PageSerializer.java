package com.rostendev.database.storage;

import com.rostendev.database.constants.Constants;

import java.io.*;

public class PageSerializer {

    /**
     * Convierte una Page en exactamente 4096 bytes.
     */
    public byte[] serialize(Page page) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.PAGE_SIZE);
        DataOutputStream out = new DataOutputStream(byteStream);

        /*
         * =========================
         * HEADER
         * =========================
         */
        out.writeShort(page.getSlotCount());
        out.writeShort(page.getFreeStart());
        out.writeShort(page.getFreeEnd());

        // Reservado para uso futuro
        out.writeShort(0);

        /*
         * =========================
         * SLOT DIRECTORY
         * =========================
         */
        for (Slot slot : page.getSlots()) {
            out.writeInt(slot.getOffset());
            out.writeInt(slot.getLength());
        }

        /*
         * =========================
         * RESTO DE LA PÁGINA
         * =========================
         *
         * La Page ya contiene los 4096 bytes físicos.
         *
         * Como ya escribimos header + slots,
         * copiamos únicamente los bytes restantes.
         */

        byte[] pageDate = page.getData();
        int metadataSize = Constants.HEADER_SIZE + page.getSlotCount() * Constants.SLOT_SIZE;
        out.write(pageDate, metadataSize, Constants.PAGE_SIZE - metadataSize);
        out.flush();
        byte[] result = byteStream.toByteArray();
        if (result.length != Constants.PAGE_SIZE)
            throw new IllegalArgumentException("La pagina serializada debe tener "
                    + Constants.PAGE_SIZE + " bytes pero tiene " + result.length);

        return result;
    }

    /** Reconstruye una Page a partir de exactamente 4096 bytes.*/
    public Page deserialize(byte[] data, int pageId) throws IOException {
        if (data == null) throw new IllegalArgumentException("Data no puede ser null");
        if (data.length != Constants.PAGE_SIZE)
            throw new IllegalArgumentException("Una pagina debe tener " + Constants.PAGE_SIZE + " bytes.");

        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(byteStream);

        /* =========================
         * HEADER
         * =========================*/

        int slotCount = in.readUnsignedShort();
        int freeStart = in.readUnsignedShort();
        int freeEnd = in.readUnsignedShort();

        //Por ahora ignoramos reserved
        in.readUnsignedShort();

        /* =========================
         * VALIDACIONES
         * =========================*/

        int metadataSize = Constants.HEADER_SIZE + slotCount*Constants.SLOT_SIZE;
        if (metadataSize > Constants.PAGE_SIZE) throw new IOException("El slot directory excede el tamaño de la pagina");
        if (freeStart < metadataSize || freeStart > Constants.PAGE_SIZE)
            throw new IOException("FreeStart invalido " + freeStart);
        if (freeEnd < freeStart || freeEnd > Constants.PAGE_SIZE)
            throw new IOException("FreeEnd invalido " + freeEnd);

        /*=========================
         * CREAR PAGE
         *=========================*/
        Page page = new Page(pageId);
        page.setFreeStart(freeStart);
        page.setFreeEnd(freeEnd);

        /*=========================
         * SLOT DIRECTORY
         *=========================*/
        for (int i = 0; i < slotCount; i++) {
            int offset = in.readInt();
            int length = in.readInt();

            validateSlot(offset, length, metadataSize);
            page.addSlot(new Slot(offset, length));
        }
        validateSlotOverlaps(page);
        /*=========================
         * DATOS FÍSICOS
         * =========================
         * El header y los slots ya fueron leídos.
         * Ahora copiamos el resto de los bytes
         * directamente al array físico de Page.*/
        int dataOffset = metadataSize;
        byte[] pageData = page.getData();
        System.arraycopy(data, dataOffset, pageData, dataOffset, Constants.PAGE_SIZE - dataOffset);
        return page;
    }

    private void validateSlot(int offset, int length, int metadataSize) throws IOException{
        if (length == 0) return;
        if (offset < metadataSize || offset >= Constants.PAGE_SIZE)
            throw new IOException("Offset de slot invalido: " + offset);
        if (length < 0 || offset + length > Constants.PAGE_SIZE)
            throw new IOException("Length de slot invalido: " + length);
    }

    private void validateSlotOverlaps(Page page) throws IOException {
        System.out.println("===== PAGE " + page.getPageId() + " SLOTS =====");

        for (int i = 0; i < page.getSlotCount(); i++) {
            Slot current = page.getSlot(i);
            if (current.isFree()) continue;
            int currentStart = current.getOffset();
            int currentEnd = currentStart + current.getLength();
            for (int j = i+1; j < page.getSlotCount(); j++) {
                Slot other = page.getSlot(j);
                if (other.isFree()) continue;
                int otherStart = other.getOffset();
                int otherEnd = otherStart + other.getLength();
                boolean overlaps = currentStart < otherEnd && otherStart < currentEnd;
                if (overlaps) throw new IOException("Solapamiento entre slots "+ i + " y " + j);
            }
        }
    }
}
