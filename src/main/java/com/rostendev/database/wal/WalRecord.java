package com.rostendev.database.wal;

import com.rostendev.database.constants.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class WalRecord {
    private static final int MAGIC =  0x57414C31;
    private static final short VERSION = 1;
    private final int pageId;
    private final byte[] pageData;

    public WalRecord(int pageId, byte[] pageData) {
        if (pageId < 0) throw new IllegalArgumentException("pageid no puede ser negativo");
        if (pageData == null) throw new IllegalArgumentException("pageData no puede ser nulo");
        if (pageData.length != Constants.PAGE_SIZE) throw new IllegalArgumentException("pageData debe tener " + Constants.PAGE_SIZE + " bytes");

        this.pageId = pageId;
        this.pageData = pageData;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeShort(VERSION);
        out.writeInt(pageId);
        out.writeInt(pageData.length);
        out.write(pageData);
        out.writeInt(calculateCrc());
    }

    public static WalRecord read(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("Magic WAL invalido");
        short version = in.readShort();
        if (version != VERSION) throw new IOException("Version WAL no soportada: " + version);
        int pageId = in.readInt();
        int length = in.readInt();
        if (pageId < 0) throw new IOException("PageId WAL invalido: " + pageId);
        if (length != Constants.PAGE_SIZE) throw new IOException("Tamaño de pagina WAL invalido: " + length);
        byte[] pageData = new byte[length];
        in.readFully(pageData);
        int storedCrc = in.readInt();
        WalRecord record = new WalRecord(pageId, pageData);
        if (storedCrc != record.calculateCrc()) throw new IOException("CRC WAL invalido para la pagina " + pageId);
        return record;
    }

    private int calculateCrc(){
        CRC32 crc = new CRC32();
        crc.update(pageData);
        return (int) crc.getValue();
    }


    public int getPageId() {
        return pageId;
    }

    public byte[] getPageData() {
        return pageData;
    }
}
