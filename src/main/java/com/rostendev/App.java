package com.rostendev;

import com.rostendev.database.table.Table;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.records.Record;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.*;

import java.math.BigInteger;

/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args )  throws Exception {

        try {

            /* =====================================================
             * SCHEMA
             * ===================================================== */

            Schema schema = new Schema("persona");

            schema.addColumn(new ColumnDefinition(
                    "id",
                    DataType.BIGINT,
                    50,
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


            /* =====================================================
             * TABLE
             * ===================================================== */

            Table table = new Table(
                    "mi_database",
                    "public_string",
                    schema
            );


            /* =====================================================
             * INSERT JUAN
             * ===================================================== */

            Record juan = new Record(schema);

            juan.set(0, new BigInteger("123456789012345678901234567890123456789"));
            juan.set(1, "Juan");
            juan.set(2, 30);
            juan.set(3, true);

//            RecordPointer pointerJuan = table.insert(juan);
//
//            System.out.println("Juan insertado:");
//            System.out.println(pointerJuan);


            /* =====================================================
             * INSERT PEDRO
             * ===================================================== */

            Record pedro = new Record(schema);

            pedro.set(0, new BigInteger("123456789012345678901234567890123456788"));
            pedro.set(1, "Pedro");
            pedro.set(2, 25);
            pedro.set(3, false);

//            RecordPointer pointerPedro = table.insert(pedro);
//
//            System.out.println("\nPedro insertado:");
//            System.out.println(pointerPedro);


            /* =====================================================
             * FIND JUAN
             * ===================================================== */

            Record resultJuan = table.find(new BigInteger("123456789012345678901234567890123456789"));

            System.out.println("\nResultado búsqueda Juan:");

            if (resultJuan != null) {

                System.out.println("ID: " + resultJuan.get(0));
                System.out.println("Nombre: " + resultJuan.get(1));
                System.out.println("Edad: " + resultJuan.get(2));
                System.out.println("Activo: " + resultJuan.get(3));

            } else {

                System.out.println("Juan no encontrado");
            }


            /* =====================================================
             * FIND PEDRO
             * ===================================================== */

            Record resultPedro = table.find(new BigInteger("123456789012345678901234567890123456788"));

            System.out.println("\nResultado búsqueda Pedro:");

            if (resultPedro != null) {

                System.out.println("ID: " + resultPedro.get(0));
                System.out.println("Nombre: " + resultPedro.get(1));
                System.out.println("Edad: " + resultPedro.get(2));
                System.out.println("Activo: " + resultPedro.get(3));

            } else {

                System.out.println("Pedro no encontrado");
            }


            /* =====================================================
             * FIND INEXISTENTE
             * ===================================================== */

            Record result = table.find(new BigInteger("123456789012345678901234567890123456787"));

            System.out.println("\nBúsqueda ID inexistente:");

            if (result == null) {
                System.out.println("No encontrado");
            } else {
                System.out.println("ERROR: encontró un registro que no existe");
            }


            /* =====================================================
             * PK DUPLICADA
             * ===================================================== */

            System.out.println("\nProbando PK duplicada:");

            try {

                Record duplicate = new Record(schema);

                duplicate.set(0, new BigInteger("123456789012345678901234567890123456789"));
                duplicate.set(1, "Otro");
                duplicate.set(2, 50);
                duplicate.set(3, true);

                table.insert(duplicate);

                System.out.println(
                        "ERROR: permitió una PK duplicada"
                );

            } catch (IllegalArgumentException e) {

                System.out.println(
                        "Correcto: " + e.getMessage()
                );
            }


        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}
