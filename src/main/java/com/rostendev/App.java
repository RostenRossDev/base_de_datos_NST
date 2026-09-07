package com.rostendev;

import com.rostendev.database.Table;
import com.rostendev.database.constants.Constants;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.records.Record;
import com.rostendev.database.records.RecordSerializer;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args )  throws Exception {

//        Schema schema = new Schema("test");
//        schema.addColumn(new ColumnDefinition("byteValue", DataType.BYTE, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("shortValue", DataType.SHORT, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("intValue", DataType.INT, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("LongValue", DataType.LONG, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("doubleValue", DataType.DOUBLE, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("stringValue", DataType.STRING, 50, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("bigDecimalValue", DataType.BIGDECIMAL, null, false,false,false,false));
//        schema.addColumn(new ColumnDefinition("bitIntValue", DataType.BIGINT, null, false,false,false,false));
//
//        Record record = new Record(schema);
//
//        record.set(0, (byte) 10);
//        record.set(1, (short) 1000);
//        record.set(2, 50000);
//        record.set(3, 9999999999L);
//        record.set(4, 123.456);
//        record.set(5, "Hola");
//        record.set(6, new BigDecimal("123456789.12345"));
//        record.set(7, new BigInteger("123456789012345678901234567890"));
//
//        RecordSerializer serializer =
//                new RecordSerializer();
//
//        byte[] data =
//                serializer.serialize(record);
//
//        System.out.println(
//                "Bytes: " + data.length
//        );
//
//        Record restored =
//                serializer.deserialize(data, schema);
//
//        for (int i = 0;
//             i < restored.getSchema().getColumns().size();
//             i++) {
//
//            System.out.println(
//                    restored.getSchema()
//                            .getColumns()
//                            .get(i)
//                            .getName()
//                            + " = "
//                            + restored.get(i)
//            );
//        }
        Schema schema = new Schema("Persona");

        schema.addColumn(new ColumnDefinition(
                "id",
                DataType.LONG,
                null,
                false,
                true,
                false,
                true
        ));

        schema.addColumn(new ColumnDefinition(
                "nombre",
                DataType.STRING,
                100,
                false,
                false,
                false,
                false
        ));

        schema.addColumn(new ColumnDefinition(
                "edad",
                DataType.INT,
                null,
                true,
                false,
                false,
                false
        ));

        schema.addColumn(new ColumnDefinition(
                "activo",
                DataType.BOOLEAN,
                null,
                false,
                false,
                false,
                false
        ));

        Table table = new Table(
                "mi_base",
                "public",
                schema
        );

        /*
         * =========================
         * INSERTAR REGISTROS
         * =========================
         */

        Record persona1 = new Record(schema);
        persona1.set(0, 1L);
        persona1.set(1, "Juan");
        persona1.set(2, 35);
        persona1.set(3, true);

        RecordPointer pointer1 =
                table.insert(persona1);


        Record persona2 = new Record(schema);
        persona2.set(0, 2L);
        persona2.set(1, "Pedro");
        persona2.set(2, 28);
        persona2.set(3, true);

        RecordPointer pointer2 =
                table.insert(persona2);


        /*
         * Este tiene edad NULL.
         */

        Record persona3 = new Record(schema);
        persona3.set(0, 3L);
        persona3.set(1, "Maria");
        persona3.set(2, null);
        persona3.set(3, false);

        RecordPointer pointer3 =
                table.insert(persona3);


        /*
         * =========================
         * MOSTRAR POINTERS
         * =========================
         */

        System.out.println("POINTERS:");

        System.out.println(
                "Juan  -> " + pointer1
        );

        System.out.println(
                "Pedro -> " + pointer2
        );

        System.out.println(
                "Maria -> " + pointer3
        );


        /*
         * =========================
         * LEER REGISTROS
         * =========================
         */

        System.out.println("\nREGISTROS:");

        printRecord(
                table.read(pointer1),
                schema
        );

        printRecord(
                table.read(pointer2),
                schema
        );

        printRecord(
                table.read(pointer3),
                schema
        );


        /*
         * =========================
         * INFORMACIÓN DE LA TABLA
         * =========================
         */

        System.out.println("\nARCHIVO:");

        System.out.println(
                "Páginas: "
                        + table.getDataFile().getPageCount()
        );


        table.close();
    }

    private static void printRecord(
            Record record,
            Schema schema) {

        for (int i = 0;
             i < schema.getColumns().size();
             i++) {

            System.out.println(
                    schema.getColumns()
                            .get(i)
                            .getName()
                            + " = "
                            + record.get(i)
            );
        }

        System.out.println();


    }
}
