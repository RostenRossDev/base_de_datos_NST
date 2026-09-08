package com.rostendev.database.index;

import com.rostendev.database.schema.DataType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IndexFile {
    private static final String MAGIC = "IDX2";
    private static final int VERSION = 2;
    private static final int PAGE_SIZE = 4096;
    private static final int METADATA_PAGE = 0;
    private static final int FIRST_NODE_PAGE = 1;
    private static final int NODE_HEADER_SIZE = 16;

    private final Path filePath;
    private final DataType keyType;

    public IndexFile(Path filePath,  DataType keyType) throws IOException {
        if (filePath == null) throw new IllegalArgumentException("filePath no puede ser null");
        if (keyType == null) throw new IllegalArgumentException("keyType no puede ser null");
        this.keyType = keyType;
        this.filePath = filePath;
        Path parent = filePath.getParent();
        if (parent != null) Files.createDirectories(parent);

        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")) {
            if (file.length() == 0){
                initialize(file);
            } else {
                validateMetadata(file);
            }
        }
    }

    public BPlusTreeNode readNode(int pageNumber) throws IOException{
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            long pageOffset = (long) pageNumber * PAGE_SIZE;
            file.seek(pageOffset);
            int nodeType = file.readInt();
            int keyCount = file.readInt();
            int parentPage = file.readInt();
            int nextPage = file.readInt();

            BPlusTreeNode node = new BPlusTreeNode(pageNumber, nodeType);
            node.setParentPage(parentPage);
            node.setNextPage(nextPage);

            if (node.isLeaf()){
                for (int i = 0; i < keyCount; i++) {
                    Object key = readKey(file);
                    int dataPage = file.readInt();
                    int dataSlot = file.readInt();

                    node.getEntries().add(new IndexEntry(key, dataPage, dataSlot));
                }
            } else {
                //primer hijo
                int firstChild = file.readInt();
                node.getChildren().add(firstChild);

                //key + child
                for (int i = 0; i < keyCount; i++) {
                    Object key = readKey(file);
                    int child = file.readInt();

                    node.getKeys().add(key);
                    node.getChildren().add(child);
                }
            }
            node.updateKeyCount();
            return node;
        }
    }

    public void writeNode(BPlusTreeNode node) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")){
            long pageOffset = (long) node.getPageNumber() * PAGE_SIZE;
            file.seek(pageOffset);

            //NODE header
            file.writeInt(node.getNodeType());

            if (node.isLeaf()) {
                file.writeInt(node.getEntries().size());
            } else {
                file.writeInt(node.getKeys().size());
            }

            file.writeInt(node.getParentPage());
            file.writeInt(node.getNextPage());

            if (node.isLeaf()) {
                for (IndexEntry entry: node.getEntries()){
                    writeKey(file, entry.getKey());
                    file.writeInt(entry.getPageNumber());
                    file.writeInt(entry.getSlotNumber());
                }
            } else {
                if (node.getChildren().isEmpty()) throw new IllegalArgumentException("Un nodo interno debe tener al menos un hijo");
                //FIRST CHILD
                file.writeInt(node.getChildren().get(0));

                for (int i = 0; i < node.getKeys().size(); i++) {
                    writeKey(file,node.getKeys().get(i));
                    file.writeInt(node.getChildren().get(i +1));
                }
            }
        }
    }

    public int allocatePage() throws IOException {
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")){
            int pageNumber = (int) (file.length() / PAGE_SIZE);
            long pageOffset = (long) pageNumber * PAGE_SIZE;
            file.setLength(pageOffset + PAGE_SIZE);
            updatePageCount(file, pageNumber +1);
            return pageNumber;
        }
    }

    public int getRootPage() throws IOException {
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")){
            file.seek(12);
            return file.readInt();
        }
    }

    public void setRootPage(int rootPage) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")) {
            file.seek(12);
            file.writeInt(rootPage);
        }
    }
    public int getFirstLeafPage() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            file.seek(16);
            return file.readInt();
        }
    }

    public int getPageCount() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            file.seek(20);
            return file.readInt();
        }
    }
    private void updatePageCount(RandomAccessFile file, int pageCount) throws IOException {
        file.seek(20);
        file.writeInt(pageCount);
    }

    public DataType getKeyDataType(){
        return keyType;
    }

    private void initialize(RandomAccessFile file) throws IOException{
        //PAGINA 0 == METADATA
        writeMetadata(file, FIRST_NODE_PAGE, FIRST_NODE_PAGE, 2);
        // Página 1 = root inicial
        // Reservamos físicamente:
        // página 0 = metadata
        // página 1 = root
        file.setLength((long) PAGE_SIZE * 2);
        BPlusTreeNode root = new BPlusTreeNode(FIRST_NODE_PAGE, BPlusTreeNode.LEAF);
        writeNode(file, root);
    }

    private void writeNode(RandomAccessFile file, BPlusTreeNode node) throws IOException {
        long pageOffset = (long) node.getPageNumber() * PAGE_SIZE;
        file.seek(pageOffset);
        file.writeInt(node.getNodeType());
        if (node.isLeaf()) {
            file.writeInt(node.getEntries().size());
        } else {
            file.writeInt(node.getKeys().size());
        }

        file.writeInt(node.getParentPage());
        file.writeInt(node.getNextPage());

        if (node.isLeaf()){
            for (IndexEntry entry : node.getEntries()){
                writeKey(file,entry.getKey());
                file.writeInt(entry.getPageNumber());
                file.writeInt(entry.getSlotNumber());
            }
        } else {
            file.writeInt(node.getChildren().get(0));
            for (int i = 0; i < node.getKeys().size(); i++){
                writeKey(file,node.getKeys().get(i));
                file.writeInt(node.getChildren().get(i + 1));
            }
        }
    }

//    private void writeMetadata(RandomAccessFile file, int rootPage, int firstLeafPage, int pageCount) throws  IOException{
//        file.seek(0);
//        file.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
//        file.writeInt(VERSION);
//        file.writeInt(PAGE_SIZE);
//        file.writeInt(rootPage);
//        file.writeInt(firstLeafPage);
//        file.writeInt(pageCount);
//
//        // Completar la página de metadata
//        file.setLength(PAGE_SIZE);
//    }

    private void writeMetadata(RandomAccessFile file,int rootPage,int firstLeafPage,int pageCount) throws IOException {
        file.seek(0);
        file.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        file.writeInt(VERSION);
        file.writeInt(PAGE_SIZE);
        file.writeInt(rootPage);
        file.writeInt(firstLeafPage);
        file.writeInt(pageCount);
        /*
         * Guardamos el DataType como ordinal.
         *
         * Esto nos permite saber cómo
         * interpretar las claves al abrir
         * nuevamente el archivo.
         */

        file.writeInt(
                keyType.ordinal()
        );
        file.setLength(PAGE_SIZE);
    }

    private void validateMetadata(RandomAccessFile file) throws IOException {
        file.seek(0);
        byte[] magic =new byte[4];
        file.readFully(magic);
        String storedMagic =new String(magic,StandardCharsets.US_ASCII);
        if (!MAGIC.equals(storedMagic))
            throw new IOException("Archivo de índice inválido. MAGIC esperado: "
                            + MAGIC + ", encontrado: " + storedMagic);


        int version =
                file.readInt();

        if (version != VERSION) {

            throw new IOException(
                    "Versión de índice no compatible: "
                            + version
            );
        }

        int pageSize =
                file.readInt();

        if (pageSize != PAGE_SIZE) {

            throw new IOException(
                    "PAGE_SIZE incompatible: "
                            + pageSize
            );
        }

        /*
         * Saltamos:
         *
         * root
         * firstLeaf
         * pageCount
         */

        file.skipBytes(12);

        int storedKeyType =
                file.readInt();

        DataType storedType =
                DataType.values()[storedKeyType];

        if (storedType != keyType) {

            throw new IOException(
                    "El tipo de clave del índice es "
                            + storedType
                            + " pero se esperaba "
                            + keyType
            );
        }
    }

    private void writeKey(
            RandomAccessFile file,
            Object key)
            throws IOException {

        IndexKey.validate(
                key,
                keyType
        );

        switch (keyType) {

            case BYTE:

                file.writeByte(
                        (Byte) key
                );
                break;

            case SHORT:

                file.writeShort(
                        (Short) key
                );
                break;

            case INT:

                file.writeInt(
                        (Integer) key
                );
                break;

            case LONG:

                file.writeLong(
                        (Long) key
                );
                break;

            case DOUBLE:

                file.writeDouble(
                        (Double) key
                );
                break;

            case BOOLEAN:

                file.writeBoolean(
                        (Boolean) key
                );
                break;

            case STRING:

                byte[] stringBytes =
                        ((String) key)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                file.writeInt(
                        stringBytes.length
                );

                file.write(
                        stringBytes
                );

                break;

            case BIGINT:

                byte[] bigIntegerBytes =
                        ((BigInteger) key)
                                .toByteArray();

                file.writeInt(
                        bigIntegerBytes.length
                );

                file.write(
                        bigIntegerBytes
                );

                break;

            case BIGDECIMAL:

                BigDecimal decimal =
                        (BigDecimal) key;

                byte[] unscaled =
                        decimal.unscaledValue()
                                .toByteArray();

                file.writeInt(
                        decimal.scale()
                );

                file.writeInt(
                        unscaled.length
                );

                file.write(
                        unscaled
                );

                break;

            default:

                throw new IOException(
                        "Tipo no soportado: "
                                + keyType
                );
        }
    }

    private Object readKey(
            RandomAccessFile file)
            throws IOException {

        switch (keyType) {
            case BYTE:
                return file.readByte();
            case SHORT:
                return file.readShort();
            case INT:
                return file.readInt();
            case LONG:
                return file.readLong();
            case STRING:
                int stringLength =file.readInt();
                if (stringLength < 0) throw new IOException("Longitud de STRING inválida: "+ stringLength);
                byte[] stringBytes =new byte[stringLength];
                file.readFully(stringBytes);
                return new String(stringBytes,StandardCharsets.UTF_8);
            case BIGINT:
                int bigIntegerLength =file.readInt();
                if (bigIntegerLength <= 0)throw new IOException("Longitud de BIGINT inválida: "+ bigIntegerLength);
                byte[] bigIntegerBytes =new byte[bigIntegerLength];
                file.readFully(bigIntegerBytes);
                return new BigInteger(bigIntegerBytes);
            default:
                throw new IOException("Tipo no soportado: "+ keyType);
        }
    }
}
