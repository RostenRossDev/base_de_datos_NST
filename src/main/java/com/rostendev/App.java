package com.rostendev;

import com.rostendev.database.index.BPlusTree;
import com.rostendev.database.index.IndexEntry;
import com.rostendev.database.index.IndexFile;
import com.rostendev.database.table.Table;
import com.rostendev.database.schema.DataType;
import com.rostendev.database.records.Record;
import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;
import com.rostendev.database.storage.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws IOException {
        try {

            Path indexPath =Paths.get("test-index.idx");

            // Empezamos siempre con un archivo limpio.
            Files.deleteIfExists(indexPath);

            IndexFile indexFile = new IndexFile(indexPath, DataType.INT);

            BPlusTree tree =new BPlusTree(indexPath,DataType.INT);

            // =====================================================
            // 1. INSERTAR 1 -> 100
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 1: INSERT 1 -> 100"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 1; i <= 100; i++) {
                tree.insert(i, i ,i);
                tree.validateTree();
            }

            System.out.println(
                    "OK: INSERT 1 -> 100"
            );


            // =====================================================
            // 2. BUSCAR 1 -> 100
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 2: SEARCH 1 -> 100"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 1; i <= 100; i++) {

                IndexEntry entry =
                        tree.search(i);

                if (entry == null) {

                    throw new IllegalStateException(
                            "No se encontró la clave " + i
                    );
                }
            }

            System.out.println(
                    "OK: todas las claves fueron encontradas"
            );


            // =====================================================
            // 3. DELETE SELECTIVO
            //    2,4,6,...,100
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 3: DELETE SELECTIVO"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 2; i <= 100; i += 2) {

                System.out.println(
                        "\n========== DELETE " + i + " =========="
                );

                tree.delete(i);

                tree.printTree();

                tree.validateTree();
            }

            // Verificamos que los pares hayan desaparecido.
            for (int i = 2; i <= 100; i += 2) {

                if (tree.search(i) != null) {

                    throw new IllegalStateException(
                            "La clave eliminada sigue existiendo: "
                                    + i
                    );
                }
            }

            // Verificamos que los impares sigan existiendo.
            for (int i = 1; i <= 100; i += 2) {

                if (tree.search(i) == null) {

                    throw new IllegalStateException(
                            "La clave existente desapareció: "
                                    + i
                    );
                }
            }

            System.out.println(
                    "OK: DELETE SELECTIVO"
            );


            // =====================================================
            // 4. DELETE RESTANTES
            //    99,97,95,...,1
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 4: DELETE RESTANTES INVERSO"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 99; i >= 1; i -= 2) {

                tree.delete(i);

                tree.validateTree();
            }

            System.out.println(
                    "OK: todas las claves fueron eliminadas"
            );


            // =====================================================
            // 5. INSERTAR NUEVAMENTE EN ORDEN INVERSO
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 5: INSERT 100 -> 1"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 100; i >= 1; i--) {

                tree.insert(i,i,i);

                tree.validateTree();
            }

            System.out.println(
                    "OK: INSERT 100 -> 1"
            );


            // =====================================================
            // 6. DELETE 100 -> 1
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " PRUEBA 6: DELETE 100 -> 1"
            );
            System.out.println(
                    "========================================"
            );

            for (int i = 100; i >= 1; i--) {

                tree.delete(i);

                tree.validateTree();
            }

            System.out.println(
                    "OK: DELETE 100 -> 1"
            );


            // =====================================================
            // 7. ESTADO FINAL
            // =====================================================

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " ESTADO FINAL"
            );
            System.out.println(
                    "========================================"
            );

            tree.printTree();

            tree.validateTree();

            System.out.println(
                    "\n========================================"
            );
            System.out.println(
                    " TODAS LAS PRUEBAS COMPLETADAS"
            );
            System.out.println(
                    "========================================"
            );

        } catch (IOException e) {

            e.printStackTrace();

        } catch (Exception e) {

            System.err.println(
                    "\n===== ERROR EN LA PRUEBA ====="
            );

            e.printStackTrace();
        }
    }
}
