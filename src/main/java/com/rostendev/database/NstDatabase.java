package com.rostendev.database;

import com.rostendev.database.index.BPlusTree;
import com.rostendev.database.index.IndexEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class NstDatabase {
    private static final String MAGIC = "NST1"; //Formato de archivo de los datos
    private static final int VERSION = 1; //Version de archivo
    private static final int PAGE_SIZE = 4096; //tamaño de cada pagina
    private static final int HEADER_SIZE = 20;
    private static final int PAGE_HEADER_SIZE = 16;

    private final BPlusTree index;
    private final Path filePath;
    private final Path indexPath;

    private NstDatabase(Path filePath) throws IOException {
        this.filePath = filePath;
        this.indexPath = Path.of(filePath.toString() + ".idx");
        this.index = new BPlusTree(indexPath);
    }

    public static NstDatabase open(String fileName) throws IOException {

       Path path = Path.of(fileName + ".nst");

        // "rw" = lectura y escritura
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {

            if (file.length() == 0) {
                writeHeader(file);
                writePage(file, 0);
                updatePageCount(file, 1);
                System.out.println("Creando nueva base de datos: " + path);
            } else {
                System.out.println("Abriendo base de datos existente: " + path);
            }
        }
        return new NstDatabase(path);
    }

    public void insert(int id, String data) throws IOException     {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        int dataLength = dataBytes.length;
        // Por ahora nuestro registro tiene:
        //
        // STATUS = 1 byte    lo que ocupa la representacion del estado del registro
        // ID     = 4 bytes   lo que ocupa la representacion de su ID
        // LENGTH = 4 bytes   lo que ocupa el numero que representa la cantidad de bytes de la data. Ej :
        //                  si data ocupara 6 bytes entonces se guardaria el numero 6 que ocupa 4 bytes
        // DATA   = N bytes   lo que ocupa la data.

        int recordSize = 1 + 4 + 4 + dataLength;


        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw")){
            // Por ahora solamente usamos PAGE 0
            // El registro comienza después del PAGE HEADER
            long pageCount = getPageCount(file);
            int pageNumber = (int) pageCount - 1;
            long pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE); // Donde inicia la pagina
            int SLOT_SIZE = 8;

            file.seek(pageOffset);

            // -----------------------------------
            // LEER PAGE HEADER
            // -----------------------------------
            file.seek(pageOffset);
            int storedPageNumber = file.readInt();
            int slotCount = file.readInt();
            int freeStart = file.readInt();
            int freeEnd = file.readInt();

            System.out.println("----- PAGE HEADER -----");
            System.out.println("PAGE NUMBER: " + storedPageNumber);
            System.out.println("SLOT COUNT: " + slotCount);
            System.out.println("FREE START: " + freeStart);
            System.out.println("FREE END: " + freeEnd);

            // -----------------------------
            // CALCULAR ESPACIO NECESARIO
            // -----------------------------
            int availableSpace = freeEnd - freeStart;

            if ((recordSize + SLOT_SIZE) > availableSpace) {
                //La pagina actual esta llena
                //Creamos una pagina nueva al final

                int newPageNumber = (int) pageCount;
                writePage(file, newPageNumber);

                //ahora existe una pagina mas
                updatePageCount(file, pageCount + 1);

                //pasamos a trabajar con la nueva pagina
                pageNumber = newPageNumber;
                pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE);

                //LA PAGINA NUEVA ESTA VACIA
                slotCount = 0;
                freeStart = PAGE_HEADER_SIZE;
                freeEnd = PAGE_SIZE;
                System.out.println("Pagina llena. Creando page " + pageNumber);
            }

            // -----------------------------------
            // POSICIÓN DEL NUEVO REGISTRO
            // -----------------------------------
            long recordOffset = pageOffset + freeStart;
            file.seek(recordOffset);

            // -----------------------------------
            // ESCRIBIR REGISTRO
            // -----------------------------------

            //status
            file.writeByte(1);

            //id
            file.writeInt(id);

            // LENGTH
            file.writeInt(dataLength);

            // DATA
            file.write(dataBytes);

            // -----------------------------
            // ESCRIBIR SLOT
            // -----------------------------
            int slotOffset = freeEnd - SLOT_SIZE;
            long slotFileOffset = pageOffset + slotOffset;
            file.seek(slotFileOffset);

            // Offset relativo dentro de la página
            file.writeInt(freeStart);

            // Tamaño del registro
            file.writeInt(recordSize);


            // -----------------------------------
            // ACTUALIZAR PAGE HEADER
            // -----------------------------------

            int newFreeStart = freeStart + recordSize;
            int newFreeEnd = slotOffset;
            int newSlotCount = slotCount + 1;

            //SLOT_COUNT esta en : pageOffset + 4
            file.seek(pageOffset + 4);
            file.writeInt(newSlotCount);

            //free start esta en : pageOffset + 8
            file.seek(pageOffset + 8);
            file.writeInt(newFreeStart);

            //free start esta en : pageOffset + 12
            file.seek(pageOffset + 12);
            file.writeInt(newFreeEnd);

            // -------------------------------------------------
            // ACTUALIZAR ÍNDICE B+TREE
            // -------------------------------------------------
            index.insert(id, pageNumber, slotCount);


            // -----------------------------
            // MOSTRAR INFORMACIÓN
            // -----------------------------
            System.out.println("----- INSERT -----");
            System.out.println("ID: " + id);
            System.out.println("RECORD OFFSET: " + recordOffset);
            System.out.println("RECORD SIZE: " + recordSize);
            System.out.println("SLOT OFFSET: " + slotOffset);
            System.out.println("SLOT SIZE: " + SLOT_SIZE);
            System.out.println("PAGE: " + pageNumber);
            System.out.println("SLOT: " + slotCount);
            System.out.println("NEW SLOT COUNT: " + newSlotCount);
            System.out.println("NEW FREE START: " + newFreeStart);
            System.out.println("NEW FREE END: " + newFreeEnd);

        }
    }

    public void readRecords() throws IOException{
        int pageNumber = 0;
        long pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE);
        final int SLOT_SIZE = 8;

        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            // -----------------------------
            // LEER PAGE HEADER
            //
            file.seek(pageOffset);
            int storedPageNumber = file.readInt();
            int slotCount = file.readInt();
            int freeStart = file.readInt();
            int freeEnd = file.readInt();

            System.out.println();
            System.out.println("----- READ RECORDS -----");
            System.out.println("PAGE NUMBER: " + storedPageNumber);
            System.out.println("SLOT COUNT: " + slotCount);
            System.out.println("FREE START: " + freeStart);
            System.out.println("FREE END: " + freeEnd);

            // -----------------------------
            // LEER SLOTS
            // -----------------------------
            for (int i = 0; i < slotCount; i++){
                /*
                 * Los slots crecen hacia atrás.
                 *
                 * Ejemplo:
                 *
                 * slot 1 → 4088
                 * slot 2 → 4080
                 * slot 3 → 4072
                 *
                 * Como FREE_END apunta al comienzo
                 * del último slot agregado:
                 *
                 * FREE_END + i * SLOT_SIZE
                 */
                int slotOffset = freeEnd + ((slotCount - 1 - i) * SLOT_SIZE);
                long slotFileOffset = pageOffset + slotOffset;
                file.seek(slotFileOffset);
                int recordOffset = file.readInt();
                int recordSize = file.readInt();

                System.out.println();
                System.out.println("----- SLOT " + i + " -----");
                System.out.println("SLOT OFFSET: " + slotOffset);
                System.out.println("RECORD OFFSET: " + recordOffset);
                System.out.println("RECORD SIZE: " + recordSize);


                // -----------------------------
                // LEER REGISTRO
                // -----------------------------
                long recordFileOffset = pageOffset + recordOffset;
                file.seek(recordFileOffset);
                byte status = file.readByte();
                int id = file.readInt();
                int dataLength = file.readInt();
                byte[] dataBytes = new byte[dataLength];
                file.readFully(dataBytes);
                String data = new String(dataBytes, StandardCharsets.UTF_8);
                System.out.println("STATUS: " + status);
                System.out.println("ID: " + id);
                System.out.println("LENGTH: " + dataLength);
                System.out.println("DATA: " + data);
            }
        }
    }

    public String  read(int id) throws IOException {
        IndexEntry entry = index.search(id);
        if (entry == null) {
            System.out.println("ID no encontrado: " + id);
            return null;
        }

        int pageNumber = entry.getPageNumber();
        int slotNumber = entry.getSlotNumber();

        long pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE);
        final int SLOT_SIZE = 8;

        //leer PAGE HADER para obtener FREE_END
        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")){
            file.seek(pageOffset + 12);
            int freeEnd = file.readInt();
            int slotCount;
            file.seek(pageOffset + 4);
            slotCount = file.readInt();
            /*
             * Convertimos el número lógico de slot
             * en su posición física dentro de la página.
             *
             * slot 0 → el primero que insertamos
             * slot 1 → el segundo
             * ...
             */
            int slotOffset = freeEnd + ((slotCount -1 - slotNumber) * SLOT_SIZE);
            file.seek(pageOffset + slotOffset);
            int recordOffset = file.readInt();
            int recordSize = file.readInt();

            // Ir al registro
            file.seek(pageOffset + recordOffset);
             byte status = file.readByte();
             int storedId = file.readInt();
             int dataLength = file.readInt();
             byte[] dataBytes = new byte[dataLength];
             file.readFully(dataBytes);

             String data = new String(dataBytes, StandardCharsets.UTF_8);
//            System.out.println();
//            System.out.println("----- READ BY ID -----");
//            System.out.println("ID BUSCADO: " + id);
//            System.out.println("PAGE: " + pageNumber);
//            System.out.println("SLOT: " + slotNumber);
//            System.out.println("RECORD OFFSET: " + recordOffset);
//            System.out.println("RECORD SIZE: " + recordSize);
//            System.out.println("STATUS: " + status);
//            System.out.println("ID: " + storedId);
//            System.out.println("LENGTH: " + dataLength);
//            System.out.println("DATA: " + data);
            return data;
        }
    }
    public void readFirstRecord() throws IOException {
        int pageNumber = 0;
        long pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE);
        long recordOffset = pageOffset + PAGE_HEADER_SIZE;

        try(RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")){
            // Nos posicionamos donde comienza el primer registro
            file.seek(recordOffset);

            //status
            byte status = file.readByte();

            //id
            int id = file.readInt();

            //length
            int dataLength = file.readInt();

            //data
            byte[] dataBytes = new byte[dataLength];
            file.readFully(dataBytes);

            String data = new String(dataBytes, StandardCharsets.UTF_8);

            System.out.println("------------ REGISTRO ------------");
            System.out.println("STATUS: " + status);
            System.out.println("ID: " + id);
            System.out.println("LENGTH: " + dataLength);
            System.out.println("DATA: " + data);
        }
    }

    private static void writeHeader(RandomAccessFile file) throws IOException {

        //Escribimos el tipo de archivo para identificar despues
        file.write(MAGIC.getBytes(StandardCharsets.US_ASCII));

        //Escribimos la version para saber como tratar segun la version del archivo binario
        file.writeInt(VERSION);

        //Escribimos la longitus de cada pagina
        file.writeInt(PAGE_SIZE);

        //Escribimos la cantidad de cada paginas actuales que es 0
        file.writeLong(0);
    }

    private static void writePage(RandomAccessFile file, int pageNumber) throws IOException {

        //Calcula donde inicia la pagina indicada
        long pageOffset = HEADER_SIZE + ((long) pageNumber * PAGE_SIZE);
        //Posiciona el cursos en esa pagina
        file.seek(pageOffset);
        //Escribe la data del page header de dicha pagina
        file.writeInt(pageNumber);
        file.writeInt(0);// SLOT_COUNT
        file.writeInt(PAGE_HEADER_SIZE); //Donde inicia el espacio libre para insertar reguistros
        file.writeInt(PAGE_SIZE); //Espacio libre que al crear la pagina corresponde al espacio predefinido

        // Reservamos físicamente el resto de la página cambiando el
        file.setLength(pageOffset + PAGE_SIZE);
    }

    private void addIndexEntry(int id, int pageNumber, int slotNumber) throws  IOException{
        try (RandomAccessFile index = new RandomAccessFile(indexPath.toFile(), "rw")){
            index.seek(index.length());
            index.writeInt(id);
            index.writeInt(pageNumber);
            index.writeInt(slotNumber);
        }
    }

    private IndexEntry findEindexEntry(int id) throws IOException {
        final int INDEX_ENTRY_SIZE = 12;
        try(RandomAccessFile index = new RandomAccessFile(indexPath.toFile(), "r")){
            long entryCount = index.length() / INDEX_ENTRY_SIZE;
            for (int i = 0; i < entryCount; i++){
                index.seek((long) i * INDEX_ENTRY_SIZE);

                int storedId = index.readInt();
                int pageNumber = index.readInt();
                int slotNumber = index.readInt();

                if (storedId == id){
                    return new IndexEntry(storedId, pageNumber, slotNumber);
                }
            }
        }
        return null;
    }

    private static void updatePageCount(RandomAccessFile file, long pageCount) throws IOException {
        file.seek(12);
        file.writeLong(pageCount);
    }

    private static long getPageCount(RandomAccessFile file) throws IOException {
        file.seek(12);
        return file.readLong();
    }
}

