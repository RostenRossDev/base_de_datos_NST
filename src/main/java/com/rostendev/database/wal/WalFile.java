package com.rostendev.database.wal;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.records.Record;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WalFile implements Closeable{
    private final RandomAccessFile file;

    public WalFile(String path) throws IOException {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Path no puede ser nulo o vacio");
        File filePath = new File(path);
        File parent = filePath.getParentFile();
        if (parent != null && !parent.exists())
            if (!parent.mkdirs() && !parent.exists()) throw new IOException("No se pudo crear el directorio: " + parent);

        this.file = new RandomAccessFile(path, "rw");
    }

    public void append(WalRecord record) throws IOException {
        if (record == null) throw new IllegalArgumentException("record no puede ser null");
        file.seek(file.length());
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteStream);
        record.write(out);
        out.flush();
        byte[] data =byteStream.toByteArray();
        file.write(data);
    }

    public void flush() throws IOException {
        file.getFD().sync();
    }

    public List<WalRecord> readAll() throws IOException {
        List<WalRecord> records = new ArrayList<>();
        file.seek(0);
        final int HEADER_SIZE = 23;
        final int CRC_SIZE = 4;

        while (file.getFilePointer() < file.length()) {
            long position = file.getFilePointer();
            long remaining = file.length() - position;

            /* No alcanza ni siquiera para leer el header.
             * Se considera un registro incompleto al final.  */
            if (remaining < HEADER_SIZE + CRC_SIZE) {
                file.setLength(position);
                file.getFD().sync();
                break;
            }
            try {
                DataInputStream in = new DataInputStream(new InputStream(){
                    @Override
                    public int read() throws IOException {
                        return file.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        return file.read(b, off, len);
                    }
                });
                records.add(WalRecord.read(in));
            } catch (EOFException e) {
                /* El registro comenzó pero no llegó completo.
                 * Como estamos al final del WAL, lo descartamos. */
                file.setLength(position);
                file.getFD().sync();
                break;
            }
        }
        return  records;
    }

    public long length() throws IOException {
        return file.length();
    }

    public void truncate() throws IOException {
        file.setLength(0);
        file.getFD().sync();
    }

    public void sync() throws IOException {
        file.getFD().sync();
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
