package com.rostendev.database.wal;

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
        while (file.getFilePointer() < file.length()) {
            long position = file.getFilePointer();
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
                throw new IOException("Registro WAL incompleto en offset : " + position, e);
            }
        }
        return  records;
    }

    public long length() throws IOException {
        return file.length();
    }


    @Override
    public void close() throws IOException {
        file.close();
    }
}
