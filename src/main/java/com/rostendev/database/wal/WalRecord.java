package com.rostendev.database.wal;

import com.rostendev.database.constants.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class WalRecord {
    private static final int MAGIC =  0x57414C31;
    private static final short VERSION = 2;
    private final TransactionType type;
    private final long transactionId;
    private final int pageId;
    private final byte[] pageData;
    public static final int RECORD_SIZE = 4 + 2 + 4 + 4 + Constants.PAGE_SIZE + 4;

    public WalRecord(TransactionType type, long transactionId, int pageId, byte[] pageData) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        if (transactionId < 0) throw new IllegalArgumentException("transactionId no puede ser negativo");
        if (type == TransactionType.PAGE) {
            if (pageId < 0) throw new IllegalArgumentException("pageId no puede ser negativo");
            if (pageData == null) throw new IllegalArgumentException("pageData no puede ser null");
            if (pageData.length != Constants.PAGE_SIZE) throw new IllegalArgumentException("pageData debe tener"+
                    Constants.PAGE_SIZE + " bytes.");
        } else {
            if (pageId != -1) throw new IllegalArgumentException("BEGIN/COMMIT debe tener pageId = -1");
            if (pageData != null) throw new IllegalArgumentException("BEGIN/COMMIT no puede tener pageData");
        }
        this.type = type;
        this.transactionId = transactionId;
        this.pageId = pageId;
        this.pageData = pageData == null ? null : pageData.clone();
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeShort(VERSION);
        out.writeByte(type.ordinal());
        out.writeLong(transactionId);
        out.writeInt(pageId);
        int dataLength = pageData == null ? 0 : pageData.length;
        out.writeInt(dataLength);
        if (pageData != null) out.write(pageData);
        out.writeInt(calculateCrc());
    }

    public static WalRecord begin(long transactionId) {
        return new WalRecord(TransactionType.BEGIN, transactionId, -1, null);
    }

    public static WalRecord page(long transactionId, int pageId, byte[] pageData) {
        return new WalRecord(TransactionType.PAGE, transactionId, pageId, pageData);
    }

    public static WalRecord commit(long transactionId) {
        return new WalRecord(TransactionType.COMMIT, transactionId, -1, null);
    }

    public static WalRecord read(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("Magic WAL invalido");
        short version = in.readShort();
        if (version != VERSION) throw new IOException("Version WAL no soportada: " + version);

        int typeValue = in.readUnsignedByte();
        if (typeValue >= TransactionType.values().length) throw new IOException("Tipo WAL invalido: " + typeValue);
        TransactionType type = TransactionType.values()[typeValue];
        long transactionId = in.readLong();
        if (transactionId < 0) throw new IOException("transactionId WAL invalido");

        int pageId = in.readInt();
        int length = in.readInt();

        if (type == TransactionType.PAGE){
            if (pageId < 0) throw new IOException("PageId WAL invalido: " + pageId);
            if (length != Constants.PAGE_SIZE) throw new IOException("Tamaño de pagina WAL invalido: " + length);
        } else {
            if (pageId != -1) throw new IOException("BEGIN/COMMIT tiene pageId invalido");
            if (length != 0) throw new IOException("BEGIN/COMMIT no pueden tener datos");
        }
        byte[] pageData = null;
        if (length > 0) {
            pageData = new byte[length];
            in.readFully(pageData);
        }

        int storedCrc = in.readInt();
        WalRecord record = new WalRecord(type, transactionId, pageId, pageData);
        if (storedCrc != record.calculateCrc()) throw new IOException("CRC WAL invalido para la pagina " + pageId);
        return record;
    }

    private int calculateCrc(){
        CRC32 crc = new CRC32();
        crc.update(type.ordinal());
        for (int i = 7; i > 0; i--) {
            crc.update((int) (transactionId >>> (i*8)));
        }
        for (int i = 3; i >= 0 ; i--) {
            crc.update(pageId >>> (i*8) & 0xFF);
        }
        if (pageData != null) crc.update(pageData);
        return (int) crc.getValue();
    }


    public int getPageId() {
        return pageId;
    }

    public byte[] getPageData() {
        return pageData;
    }

    public TransactionType getType() {
        return type;
    }

    public long getTransactionId() {
        return transactionId;
    }
}
