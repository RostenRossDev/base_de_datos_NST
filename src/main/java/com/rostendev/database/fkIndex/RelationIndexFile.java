package com.rostendev.database.fkIndex;

import com.rostendev.database.constants.Constants;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.storage.RecordPointer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RelationIndexFile<P, F> implements AutoCloseable {

    private static final String MAGIC = "RIX1";
    private static final int VERSION = 1;
    private static final int PAGE_SIZE = Constants.PAGE_SIZE;
    private static final int METADATA_PAGE = 0;
    private static final int FIRST_NODE_PAGE = 1;
    private static final int NODE_HEADER_SIZE = 20;

    private final Path filePath;

    private final DataType parentKeyType;
    private final DataType foreignKeyType;

    public RelationIndexFile(Path filePath, DataType PKType, DataType FKType) throws IOException {
        if (filePath == null) throw new IllegalArgumentException("filePath no puede ser null");
        if (PKType == null) throw new IllegalArgumentException("parentKeyType no puede ser null");
        if (FKType == null) throw new IllegalArgumentException("foreignKeyType no puede ser null");

        this.filePath = filePath;
        this.parentKeyType = PKType;
        this.foreignKeyType = FKType;

        Path parent = filePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(filePath)) {
            try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")) {
                initialize(file);
            }
        } else {
            validateMetadata();
        }
    }

    public RelationNode<P, F> readNode(int pageNumber) throws IOException {
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            long offset = (long) pageNumber * PAGE_SIZE;
            if (offset >= file.length()) throw new IOException("No existe la pagina " + pageNumber);
            file.seek(offset);
            int nodeType = file.readInt();
            int entryCount = file.readInt();
            int parentPage = file.readInt();
            int nextPage = file.readInt();
            int reserved = file.readInt();
            RelationNode<P, F> node = new RelationNode<>(pageNumber, nodeType);
            node.setParentPage(parentPage);
            node.setNextPage(nextPage);
            if (node.isLeaf()) {
                for (int i = 0; i < entryCount; i++) {
                    P parentKey = (P) readKey(file, parentKeyType);
                    F foreignKey = (F) readKey(file, foreignKeyType);
                    int page = file.readInt();
                    int slot = file.readInt();
                    node.getEntries().add(new RelationEntry<>(parentKey, foreignKey, new RecordPointer(page, (short) slot)));
                }
            } else {
                if (entryCount > 0) {
                    int firstChild = file.readInt();
                    node.getChildren().add(firstChild);
                    for (int i = 0; i < entryCount; i++) {
                        P key = (P) readKey(file, parentKeyType);
                        int child = file.readInt();
                        node.getKeys().add(key);
                        node.getChildren().add(child);
                    }
                }
            }
            return  node;
        }
    }

    public void writeNode(RelationNode<P, F> node) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"rw")) {
            writeNode(file, node);
        }
    }

    private void writeNode(RandomAccessFile file,RelationNode<P, F> node) throws IOException {
        long offset = (long) node.getPageNumber() * PAGE_SIZE;
        file.seek(offset);
        int entryCount = node.isLeaf() ? node.getEntries().size(): node.getKeys().size();
        file.writeInt(node.isLeaf() ? RelationNode.LEAF : RelationNode.INTERNAL);
        file.writeInt(entryCount);
        file.writeInt(node.getParentPage());
        file.writeInt(node.getNextPage());
        file.writeInt(0);
        if (node.isLeaf()) {
            for (RelationEntry<P, F> entry : node.getEntries()) {
                writeKey(file,entry.getParentKey(),parentKeyType);
                writeKey(file,entry.getForeignKey(),foreignKeyType);
                RecordPointer pointer =entry.getPointer();
                file.writeInt(pointer.getPageId());
                file.writeInt(pointer.getSlotId());
            }

        } else {
            if (!node.getChildren().isEmpty()) {
                file.writeInt(node.getChildren().getFirst());
                for (int i = 0;i < node.getKeys().size();i++) {
                    writeKey(file,node.getKeys().get(i),parentKeyType);
                    file.writeInt(node.getChildren().get(i + 1));
                }
            }
        }
    }

    public int allocatePage() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"rw")) {
            long length = file.length();
            int pageNumber = (int) (length / PAGE_SIZE);
            file.setLength(length + PAGE_SIZE);
            return pageNumber;
        }
    }

    public int getRootPage() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"r")) {
            file.seek(12);
            return file.readInt();
        }
    }

    public void setRootPage(int pageNumber) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"rw")) {
            file.seek(12);
            file.writeInt(pageNumber);
        }
    }

    public int getFirstLeafPage() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"r")) {
            file.seek(16);
            return file.readInt();
        }
    }

    public int getPageCount() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"r")) {
            file.seek(20);
            return file.readInt();
        }
    }

    public DataType getParentKeyType() {
        return parentKeyType;
    }

    public DataType getForeignKeyType() {
        return foreignKeyType;
    }

    private void initialize(RandomAccessFile file) throws IOException {
        file.setLength((long) PAGE_SIZE * 2);
        writeMetadata(file,FIRST_NODE_PAGE,FIRST_NODE_PAGE,2);
        RelationNode<P, F> root = new RelationNode<>(FIRST_NODE_PAGE,RelationNode.LEAF);
        writeNode(file, root);
    }

    private void writeMetadata(RandomAccessFile file,int rootPage,
            int firstLeafPage,int pageCount) throws IOException {

        file.seek(0);
        file.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        file.writeInt(VERSION);
        file.writeInt(PAGE_SIZE);
        file.writeInt(rootPage);
        file.writeInt(firstLeafPage);
        file.writeInt(pageCount);
        file.writeInt(parentKeyType.ordinal());
        file.writeInt(foreignKeyType.ordinal());
    }

    private void validateMetadata() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(),"r")) {
            byte[] magic = new byte[4];
            file.readFully(magic);
            String storedMagic = new String(magic,StandardCharsets.US_ASCII);
            if (!MAGIC.equals(storedMagic))
                throw new IOException("Archivo RelationIndex invalido: MAGIC incorrecto");

            int version = file.readInt();
            if (version != VERSION)
                throw new IOException("Version de RelationIndex no soportada: "+ version);

            int pageSize = file.readInt();
            if (pageSize != PAGE_SIZE) throw new IOException("PAGE_SIZE incompatible");
            file.skipBytes(12);
            int storedParentType = file.readInt();
            int storedForeignType = file.readInt();
            if (storedParentType != parentKeyType.ordinal()) {
                throw new IOException("El tipo de la clave padre no coincide");
            }

            if (storedForeignType != foreignKeyType.ordinal()) {
                throw new IOException("El tipo de la FOREIGN KEY no coincide");
            }
        }
    }

    private void writeKey(RandomAccessFile file,Object key,DataType type) throws IOException {
        switch (type) {
            case BYTE -> file.writeByte(((Byte) key));
            case SHORT -> file.writeShort(((Short) key));
            case INT -> file.writeInt(((Integer) key));
            case LONG -> file.writeLong(((Long) key));
            case DOUBLE -> file.writeDouble(((Double) key));
            case BOOLEAN -> file.writeBoolean(((Boolean) key));
            case STRING -> {
                byte[] bytes = ((String) key).getBytes(StandardCharsets.UTF_8);
                file.writeInt(bytes.length);
                file.write(bytes);
            }
            case BIGINT -> {
                byte[] bytes = ((java.math.BigInteger) key).toByteArray();
                file.writeInt(bytes.length);
                file.write(bytes);
            }
            case BIGDECIMAL -> {
                java.math.BigDecimal value = (java.math.BigDecimal) key;
                file.writeInt(value.scale());
                byte[] bytes = value.unscaledValue().toByteArray();
                file.writeInt(bytes.length);
                file.write(bytes);
            }
        }
    }

    private Object readKey(RandomAccessFile file, DataType type) throws IOException {
        return switch (type) {
            case BYTE -> file.readByte();
            case SHORT -> file.readShort();
            case INT -> file.readInt();
            case LONG -> file.readLong();
            case DOUBLE -> file.readDouble();
            case BOOLEAN -> file.readBoolean();
            case STRING -> {
                int length = file.readInt();
                byte[] bytes = new byte[length];
                file.readFully(bytes);
                yield new String(bytes,StandardCharsets.UTF_8);
            }
            case BIGINT -> {
                int length = file.readInt();
                byte[] bytes = new byte[length];
                file.readFully(bytes);
                yield new java.math.BigInteger(bytes);
            }
            case BIGDECIMAL -> {
                int scale = file.readInt();
                int length = file.readInt();
                byte[] bytes = new byte[length];
                file.readFully(bytes);
                yield new java.math.BigDecimal(new java.math.BigInteger(bytes),scale);
            }
        };
    }

    @Override
    public void close() {
        // Actualmente no mantenemos el archivo abierto.

    }

}
