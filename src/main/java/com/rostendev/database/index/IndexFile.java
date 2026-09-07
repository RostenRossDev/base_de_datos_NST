package com.rostendev.database.index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class IndexFile {
    private static final String MAGIC = "IDX1";
    private static final int VERSION = 1;
    private static final int PAGE_SIZE = 4096;
    private static final int METADATA_PAGE = 0;
    private static final int FIRST_NODE_PAGE = 1;
    private static final int NODE_HEADER_SIZE = 16;

    private final Path filePath;

    public IndexFile(Path filePath) throws IOException {
        this.filePath = filePath;
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")) {
            if (file.length() == 0){
                initialize(file);
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
                    int id = file.readInt();
                    int dataPage = file.readInt();
                    int dataSlot = file.readInt();

                    node.getEntries().add(new IndexEntry(id, dataPage, dataSlot));
                }
            } else {
                //primer hijo
                int firstChild = file.readInt();
                node.getChildren().add(firstChild);

                //key + child
                for (int i = 0; i < keyCount; i++) {
                    int key = file.readInt();
                    int child = file.readInt();

                    node.getKeys().add(key);
                    node.getChildren().add(child);
                }
            }
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
                    file.writeInt(entry.getId());
                    file.writeInt(entry.getPageNumber());
                    file.writeInt(entry.getSlotNumber());
                }
            } else {
                //FIRST CHILD
                file.writeInt(node.getChildren().get(0));

                for (int i = 0; i < node.getKeys().size(); i++) {
                    file.writeInt(node.getKeys().get(i));
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
                file.writeInt(entry.getId());
                file.writeInt(entry.getPageNumber());
                file.writeInt(entry.getSlotNumber());
            }
        } else {
            file.writeInt(node.getChildren().get(0));
            for (int i = 0; i < node.getKeys().size(); i++){
                file.writeInt(node.getKeys().get(i));
                file.writeInt(node.getChildren().get(i + 1));
            }
        }
    }

    private void writeMetadata(RandomAccessFile file, int rootPage, int firstLeafPage, int pageCount) throws  IOException{
        file.seek(0);
        file.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        file.writeInt(VERSION);
        file.writeInt(PAGE_SIZE);
        file.writeInt(rootPage);
        file.writeInt(firstLeafPage);
        file.writeInt(pageCount);

        // Completar la página de metadata
        file.setLength(PAGE_SIZE);
    }


}
