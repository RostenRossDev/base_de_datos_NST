package com.rostendev.database.index;

import com.rostendev.database.schema.DataType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BPlusTree {
    private final IndexFile indexFile;
    private final DataType keyType;
    private static final int MAX_LEAF_ENTRIES = 3;
    private static final int MAX_INTERNAL_KEYS = 3;

    private static final int MIN_LEAF_ENTRIES = (MAX_LEAF_ENTRIES + 1) / 2;
    private static final int MIN_INTERNAL_CHILDREN = (MAX_INTERNAL_KEYS + 1) / 2;

    /*
     * Con un máximo de 3 entradas:
     * Hoja:
     * mínimo = ceil(3 / 2) = 2
     * Nodo interno:
     * máximo de claves = 3
     * máximo de hijos = 4
     * mínimo de hijos = 2
     * mínimo de claves = 1
     */
    public BPlusTree(Path indexPath, DataType keyType) throws IOException {
        if (keyType == null) throw new IllegalArgumentException("keyType no puede ser null");
        this.keyType = keyType;
        this.indexFile = new IndexFile(indexPath, keyType);
    }

    public void insert(Object key, int dataPage, int dataSlot) throws IOException {
        IndexEntry entry = new IndexEntry(key, dataPage, dataSlot);
        IndexKey.validate(key, keyType);

        //1. Empezamos en la raiz
        BPlusTreeNode node = indexFile.readNode(indexFile.getRootPage());

        //2. Bjamos hasta encontrar una hoja.
        while (node.isInternal()) {
            int childPage = findChildPage(node, key);
            node = indexFile.readNode(childPage);
        }

        //3. Ahora "node" es la hoja correcta
        insertIntoLeaf(node, entry);
    }

    public IndexEntry search(Object key) throws IOException {
        if (key == null) throw new IllegalArgumentException("La clave no puede ser null");
        BPlusTreeNode node = indexFile.readNode(indexFile.getRootPage());
        IndexKey.validate(key, keyType);
        //bajamos desde la raiz hasta uan hoja.
        while (node.isInternal()) {
            int childPage = findChildPage(node, key);
            node = indexFile.readNode(childPage);
        }
        //Estamos en una hoja
        for (IndexEntry entry : node.getEntries()) {
            int comparison = IndexKey.compare(entry.getKey(), key, keyType);
            //encontramos y retornamos el entry
            if (comparison == 0) return entry;

            //Como la hoja esta ordenada, si ya pasamos el id podemos terminar.
            if (comparison > 0) return null;
        }

        return null;
    }

    public void delete(Object key) throws IOException {
        if (key == null) throw new IllegalArgumentException("La clave no puede ser null");
        IndexKey.validate(key, keyType);
        // 1. Encontramos la hoja
        BPlusTreeNode leaf = findLeaf(key);


        // 2. Buscamos la clave
        int position = findKeyPosition(leaf, key);


        // La clave no existe
        if (position == -1) return;
        // 3. Eliminamos la entrada
        leaf.getEntries().remove(position);
        leaf.updateKeyCount();
        // 4. Si la hoja es la raíz, puede quedar incluso vacía
        if (leaf.getParentPage() == -1) {
            indexFile.writeNode(leaf);
            return;
        }
        // 5. Si todavía cumple el mínimo, solamente
        // actualizamos los separadores.
        if (leaf.getEntries().size() >= MIN_LEAF_ENTRIES) {
            indexFile.writeNode(leaf);
            refreshParentKeys(leaf);
            return;
        }
        // 6. La hoja quedó en underflow.
        rebalanceLeaf(leaf);
    }

    private BPlusTreeNode findLeaf(Object key) throws IOException {
        BPlusTreeNode node = indexFile.readNode(indexFile.getRootPage());
        while (node.isInternal()) {
            int childPage = findChildPage(node, key);
            node = indexFile.readNode(childPage);
        }
        return node;
    }

    private int findKeyPosition(BPlusTreeNode leaf, Object key) {
        for (int i = 0; i < leaf.getEntries().size(); i++) {
            Object currentKey = leaf.getEntries().get(i).getKey();
            int comparison = IndexKey.compare(currentKey, key, keyType);
            if (comparison == 0) return i;
            if (comparison > 0) return -1;
        }
        return -1;
    }

    private int findChildPage(BPlusTreeNode node, Object key) {
        /*
         * * Ejemplo:
         *  * keys:
         *  * [30 | 50]
         *  * children:
         *  * [1 | 2 | 3]
         *  * ID 10 -> child 1
         *  * ID 30 -> child 2
         *  * ID 40 -> child 2
         *  * ID 50 -> child 3
         *  * ID 70 -> child 3 */
        List<Object> keys = node.getKeys();
        List<Integer> children = node.getChildren();
        int position = 0;
//        while (position < keys.size() && IndexKey.compare(key, keys.get(position), keyType) >= 0) {
//            position++;
//        }
        while (position < keys.size()) {

            int comparison = IndexKey.compare(
                    key,
                    keys.get(position),
                    keyType
            );
            if (comparison < 0) {
                break;
            }

            position++;
        }
        return children.get(position);
    }

    private void insertIntoLeaf(BPlusTreeNode leaf, IndexEntry entry) throws IOException {
        int position = 0;
        while (position < leaf.getEntries().size()
                && IndexKey.compare(leaf.getEntries().get(position).getKey(), entry.getKey(), keyType) < 0) {
            position++;
        }

        // No permitimos IDs duplicados
        if (position < leaf.getEntries().size()
                && IndexKey.compare(leaf.getEntries().get(position).getKey(), entry.getKey(), keyType) == 0) {
            throw new IllegalArgumentException("El id ya existe: " + entry.getKey());
        }

        leaf.getEntries().add(position, entry);

        //todavia entra en la hoja
        if (leaf.getEntries().size() <= MAX_LEAF_ENTRIES) {
            leaf.updateKeyCount();
            indexFile.writeNode(leaf);
            // Si la primera clave cambió,
            // actualizamos separadores.
            refreshParentKeys(leaf);
            return;
        }

        //la hoja se desbordo
        splitRootLeaf(leaf);
    }

    private void splitRootLeaf(BPlusTreeNode oldLeaf) throws IOException {
        /*
         *  * Ejemplo:
         *  * [10 | 20 | 30 | 40]
         *  * queda:
         *  * [10 | 20] -> [30 | 40]
         *  */
        int newLeafPage = indexFile.allocatePage();
        BPlusTreeNode newLeaf = new BPlusTreeNode(newLeafPage, BPlusTreeNode.LEAF);

        List<IndexEntry> entries = oldLeaf.getEntries();
        int middle = entries.size() / 2;
        List<IndexEntry> rightEntires = new ArrayList<>(entries.subList(middle, entries.size()));
        entries.subList(middle, entries.size()).clear();
        newLeaf.getEntries().addAll(rightEntires);

        oldLeaf.updateKeyCount();
        newLeaf.updateKeyCount();

        /* * Las hojas forman una lista.
         *  * oldLeaf -> newLeaf -> antiguo siguiente */

        newLeaf.setNextPage(oldLeaf.getNextPage());
        oldLeaf.setNextPage(newLeafPage);
        /* * La hoja antigua deja de ser root.
        /* * La primera clave de la nueva hoja
        * será el separador que subiremos al padre. */
        Object separator = newLeaf.getEntries().get(0).getKey();
        /* * Guardamos las hojas. */
        indexFile.writeNode(oldLeaf);
        indexFile.writeNode(newLeaf);

        /* * Si la hoja que se dividió era la raíz,
         *  necesitamos crear una nueva raíz. */

        if (oldLeaf.getParentPage() == -1) {
            createNewRoot(oldLeaf, newLeaf, separator);
        } else {
            /* * La hoja ya tenía padre.
             * * Agregamos el nuevo hijo al padre. */
            insertIntoParent(oldLeaf, newLeaf, separator);
        }
    }

    private void createNewRoot(BPlusTreeNode leftLeaf, BPlusTreeNode rigthLeaf, Object separator) throws IOException {
        int newRootPage = indexFile.allocatePage();
        BPlusTreeNode newRoot = new BPlusTreeNode(newRootPage, BPlusTreeNode.INTERNAL);
        newRoot.getKeys().add(separator);
        newRoot.getChildren().add(leftLeaf.getPageNumber());
        newRoot.getChildren().add(rigthLeaf.getPageNumber());
        leftLeaf.setParentPage(newRootPage);
        rigthLeaf.setParentPage(newRootPage);

        indexFile.writeNode(leftLeaf);
        indexFile.writeNode(rigthLeaf);
        indexFile.writeNode(newRoot);

        indexFile.setRootPage(newRootPage);
    }

    private void insertIntoParent(BPlusTreeNode leftChild, BPlusTreeNode rigthChild, Object separator) throws IOException {
        BPlusTreeNode parent = indexFile.readNode(leftChild.getParentPage());

        /*Encontramos donde estaba el hijo izquierdo*/
        int childPosition = parent.getChildren().indexOf(leftChild.getPageNumber());

        parent.getKeys().add(childPosition, separator);

        /*El nuevo hijo derecho va inmediatamente despues del izquiero*/
        parent.getChildren().add(childPosition + 1, rigthChild.getPageNumber());
        rigthChild.setParentPage(parent.getPageNumber());

        //El padre todavia tiene espacio
        if (parent.getKeys().size() <= MAX_INTERNAL_KEYS) {
            indexFile.writeNode(rigthChild);
            indexFile.writeNode(parent);
            return;
        }

        //El padre tambien se desborto
        splitInternal(parent);
    }

    private void splitInternal(BPlusTreeNode oldNode) throws IOException {
        /*
         Ejemplo:
         keys:
         [30 | 50 | 70 | 90]
         children:  [A | B | C | D | E]
         La clave central 70 sube.
         Queda: [70]
                /  \
          [30|50]  [90]
        */

        List<Object> keys = oldNode.getKeys();
        List<Integer> children = oldNode.getChildren();
        System.out.println(
                "SPLIT INTERNAL: page=" + oldNode.getPageNumber()
                        + " children ANTES=" + oldNode.getChildren()
                        + " keys ANTES=" + oldNode.getKeys()
        );
        int middle = keys.size() / 2;
        Object separator = keys.get(middle);
        int newPage = indexFile.allocatePage();
        BPlusTreeNode newNode = new BPlusTreeNode(newPage, BPlusTreeNode.INTERNAL);
        /* * Claves de la derecha.
         *  * La clave middle NO se copia.
         *  * Esa es la que sube al padre. */
        for (int i = middle + 1; i < keys.size(); i++) {
            newNode.getKeys().add(keys.get(i));
        }

        /* * Hijos de la derecha.
         *  * Si tenemos:
         *  * keys = [30 50 70 90]
         *  * children= [A B C D E]
         *  * y sube 70:
         *  * izquierda = [A B C]
         *  * derecha = [D E] */

        for (int i = middle + 1; i < children.size(); i++) {
            newNode.getChildren().add(children.get(i));
        }

        /* * Eliminamos la parte derecha del nodo original. */
        while (keys.size() > middle) {
            keys.remove(keys.size() - 1);
        }

        while (children.size() > middle + 1) {
            children.remove(children.size() - 1);
        }

        newNode.setParentPage(oldNode.getParentPage());

        /* * Todos los hijos que pasaron al nuevo nodo ahora tienen otro padre. */
        for (int childPage : oldNode.getChildren()) {
            BPlusTreeNode child = indexFile.readNode(childPage);
            child.setParentPage(oldNode.getPageNumber());
            indexFile.writeNode(child);
        }
        for (int childPage : newNode.getChildren()) {
            BPlusTreeNode child = indexFile.readNode(childPage);
            child.setParentPage(newPage);
            indexFile.writeNode(child);
        }
        System.out.println(
                "SPLIT INTERNAL: page=" + oldNode.getPageNumber()
                        + " children IZQ=" + oldNode.getChildren()
                        + " children DER=" + newNode.getChildren()
        );
        oldNode.updateKeyCount();
        newNode.updateKeyCount();
        indexFile.writeNode(oldNode);
        indexFile.writeNode(newNode);

        /* * ¿El nodo dividido era la raíz? */
        if (oldNode.getParentPage() == -1) {
            createNewRoot(oldNode, newNode, separator);
            System.out.println(
                    "NUEVA RAIZ: oldNode=" + oldNode.getPageNumber()
                            + " children=" + oldNode.getChildren()
                            + " newNode=" + newNode.getPageNumber()
                            + " children=" + newNode.getChildren()
            );
        } else {
            insertIntoParent(oldNode, newNode, separator);
        }
    }

    private void rebalanceLeaf(BPlusTreeNode leaf) throws IOException {
        BPlusTreeNode parent = indexFile.readNode(leaf.getParentPage());
        int position = parent.getChildren().indexOf(leaf.getPageNumber());
        /* Primero intentamos pedirle al hermano izquierdo. */
        if (position > 0) {
            BPlusTreeNode leftSibling = indexFile.readNode(parent.getChildren().get(position - 1));
            if (leftSibling.getEntries().size() > MIN_LEAF_ENTRIES) {
                IndexEntry borrowed = leftSibling.getEntries().remove(leftSibling.getEntries().size() - 1);
                leaf.getEntries().add(0, borrowed);
                leftSibling.updateKeyCount();
                leaf.updateKeyCount();
                indexFile.writeNode(leftSibling);
                indexFile.writeNode(leaf);
                rebuildKeys(parent);
                indexFile.writeNode(parent);
                refreshParentKeys(parent);
                return;
            }
        }
        /*Intentamos pedirle al hermano derecho. */
        if (position < parent.getChildren().size() - 1) {
            BPlusTreeNode rightSibling = indexFile.readNode(parent.getChildren().get(position + 1));
            if (rightSibling.getEntries().size() > MIN_LEAF_ENTRIES) {
                IndexEntry borrowed = rightSibling.getEntries().remove(0);
                leaf.getEntries().add(borrowed);
                rightSibling.updateKeyCount();
                leaf.updateKeyCount();
                indexFile.writeNode(rightSibling);
                indexFile.writeNode(leaf);
                rebuildKeys(parent);
                indexFile.writeNode(parent);
                refreshParentKeys(parent);
                return;
            }
        }
        /*Ningún hermano puede prestar. Hacemos merge.*/
        if (position > 0) {
            // Fusionamos leaf dentro del izquierdo
            BPlusTreeNode leftSibling = indexFile.readNode(parent.getChildren().get(position - 1));
            // Guardamos el siguiente del nodo que desaparece
            int nextPage = leaf.getNextPage();
            // Fusionamos
            leftSibling.getEntries().addAll(leaf.getEntries());
            // ahora leftSibling debe apuntar al siguiente de leaf
            leftSibling.setNextPage(nextPage);
            leftSibling.updateKeyCount();
            indexFile.writeNode(leftSibling);
            parent.getChildren().remove(position);
            rebuildKeys(parent);
            indexFile.writeNode(parent);
            rebalanceInternal(parent);
            return;
        } else {
            /* Somos el primer hijo. Fusionamos el derecho dentro
             * de nosotros para que el primer leaf siga siendo el mismo.*/
            BPlusTreeNode rightSibling = indexFile.readNode(parent.getChildren().get(position + 1));
            leaf.getEntries().addAll(rightSibling.getEntries());
            // MUY IMPORTANTE:
            // el nuevo leaf salta al siguiente del rightSibling
            leaf.setNextPage(rightSibling.getNextPage());
            leaf.updateKeyCount();
            indexFile.writeNode(leaf);
            parent.getChildren().remove(position + 1);
            rebuildKeys(parent);
            indexFile.writeNode(parent);
            rebalanceInternal(parent);
        }
    }

    // REBALANCE INTERNAL =========================================================
    private void rebalanceInternal(BPlusTreeNode node) throws IOException {
        /* Si es la raíz, tiene reglas especiales.*/
        if (node.getParentPage() == -1) {
            /*La raíz puede tener menos hijos que un nodo interno normal.
             * Si solamente queda un hijo, ese hijo se convierte en raíz. */
            if (node.getChildren().size() == 1) {
                int newRootPage = node.getChildren().get(0);
                BPlusTreeNode newRoot = indexFile.readNode(newRootPage);
                newRoot.setParentPage(-1);
                indexFile.writeNode(newRoot);
                indexFile.setRootPage(newRootPage);
            }
            return;
        }

        /* Un nodo interno normal necesita al menos 2 hijos. */
        if (node.getChildren().size() >= MIN_INTERNAL_CHILDREN) {
            rebuildKeys(node);
            indexFile.writeNode(node);
            refreshParentKeys(node);
            return;
        }
        /*Tenemos underflow.*/
        BPlusTreeNode parent = indexFile.readNode(node.getParentPage());
        int position = parent.getChildren().indexOf(node.getPageNumber());
        //PEDIR AL HERMANO IZQUIERDO =====================================================
        if (position > 0) {
            BPlusTreeNode leftSibling = indexFile.readNode(parent.getChildren().get(position - 1));
            if (leftSibling.getChildren().size() > MIN_INTERNAL_CHILDREN) {
                int borrowedChildPage = leftSibling.getChildren().remove(leftSibling.getChildren().size() - 1);
                node.getChildren().add(0, borrowedChildPage);
                BPlusTreeNode borrowedChild = indexFile.readNode(borrowedChildPage);
                borrowedChild.setParentPage(node.getPageNumber());
                indexFile.writeNode(borrowedChild);
                rebuildKeys(leftSibling);
                rebuildKeys(node);
                indexFile.writeNode(leftSibling);
                indexFile.writeNode(node);
                rebuildKeys(parent);
                indexFile.writeNode(parent);
                refreshParentKeys(parent);
                return;
            }
        }

        // =====================================================
        // PEDIR AL HERMANO DERECHO
        // =====================================================

        if (position < parent.getChildren().size() - 1) {
            BPlusTreeNode rightSibling = indexFile.readNode(parent.getChildren().get(position + 1));
            if (rightSibling.getChildren().size() > MIN_INTERNAL_CHILDREN) {
                int borrowedChildPage = rightSibling.getChildren().remove(0);
                node.getChildren().add(borrowedChildPage);
                BPlusTreeNode borrowedChild = indexFile.readNode(borrowedChildPage);
                borrowedChild.setParentPage(node.getPageNumber());
                indexFile.writeNode(borrowedChild);
                rebuildKeys(rightSibling);
                rebuildKeys(node);
                indexFile.writeNode(rightSibling);
                indexFile.writeNode(node);
                rebuildKeys(parent);
                indexFile.writeNode(parent);
                refreshParentKeys(parent);
                return;
            }
        }


        // =====================================================
        // MERGE
        // =====================================================

        if (position > 0) {

            /*
             * Fusionamos node dentro del izquierdo.
             */
            BPlusTreeNode leftSibling =
                    indexFile.readNode(
                            parent.getChildren()
                                    .get(position - 1)
                    );

            for (int childPage :
                    node.getChildren()) {

                leftSibling.getChildren()
                        .add(childPage);

                BPlusTreeNode child =
                        indexFile.readNode(childPage);

                child.setParentPage(
                        leftSibling.getPageNumber()
                );

                indexFile.writeNode(child);
            }

            rebuildKeys(leftSibling);

            indexFile.writeNode(leftSibling);

            parent.getChildren()
                    .remove(position);

            rebuildKeys(parent);

            indexFile.writeNode(parent);

            rebalanceInternal(parent);

        } else {

            /*
             * Somos el primer hijo.
             *
             * Fusionamos el derecho dentro
             * de nosotros.
             */
            BPlusTreeNode rightSibling =
                    indexFile.readNode(
                            parent.getChildren()
                                    .get(position + 1)
                    );

            for (int childPage :
                    rightSibling.getChildren()) {

                node.getChildren()
                        .add(childPage);

                BPlusTreeNode child =
                        indexFile.readNode(childPage);

                child.setParentPage(
                        node.getPageNumber()
                );

                indexFile.writeNode(child);
            }

            rebuildKeys(node);

            indexFile.writeNode(node);

            parent.getChildren()
                    .remove(position + 1);

            rebuildKeys(parent);

            indexFile.writeNode(parent);

            rebalanceInternal(parent);
        }
    }


    // =========================================================
    // REBUILD KEYS
    // =========================================================

    private void rebuildKeys(BPlusTreeNode node) throws IOException {
        if (!node.isInternal()) return;
        node.getKeys().clear();

        /*
         * Si tenemos:
         * children:
         * [A, B, C]
         * necesitamos:
         * keys:
         * [first(B), first(C)]
         */
        for (int i = 1; i < node.getChildren().size(); i++) {
            int childPage = node.getChildren().get(i);
            BPlusTreeNode child = indexFile.readNode(childPage);
            Object firstKey = getFirstKey(child);
            node.getKeys().add(firstKey);
        }
        node.updateKeyCount();
    }


    // =========================================================
    // GET FIRST KEY
    // =========================================================
    private Object getFirstKey(BPlusTreeNode node) throws IOException {
        /* Hoja: la primera entrada contiene directamente la primera clave.*/
        if (node.isLeaf()) {
            if (node.getEntries().isEmpty())
                throw new IllegalStateException("Una hoja no puede estar vacía al buscar su primera clave");

            return node.getEntries().get(0).getKey();
        }
        /* Nodo interno: su primera clave está en el hijo más a la izquierda.*/
        if (node.getChildren().isEmpty())
            throw new IllegalStateException("Nodo interno sin hijos");

        BPlusTreeNode firstChild = indexFile.readNode(node.getChildren().get(0));
        return getFirstKey(firstChild);
    }


    // =========================================================
    // REFRESH PARENT KEYS
    // =========================================================

    private void refreshParentKeys(BPlusTreeNode node) throws IOException {
        int parentPage = node.getParentPage();
        if (parentPage == -1) return;
        BPlusTreeNode parent = indexFile.readNode(parentPage);
        rebuildKeys(parent);
        indexFile.writeNode(parent);
        /* Si cambió la primera clave de un subárbol, el cambio puede necesitar propagarse hacia arriba.*/
        refreshParentKeys(parent);
    }

    public void printTree() throws IOException {
        int rootPage = indexFile.getRootPage();
        printNode(rootPage, 0);
    }

    private void printNode(int pageNumber, int level) throws IOException {
        BPlusTreeNode node = indexFile.readNode(pageNumber);
        String indent = "  ".repeat(level);
        if (node.isInternal()) {
            for (int childPage : node.getChildren()) {
                printNode(childPage, level + 1);
            }
        }
    }

    // VALIDATE TREE =========================================================
    public void validateTree() throws IOException {
        int rootPage = indexFile.getRootPage();
        if (rootPage < 0) throw new IllegalStateException("ROOT PAGE inválida: " + rootPage);
        BPlusTreeNode root = indexFile.readNode(rootPage);
        // La raíz siempre debe tener parent = -1
        if (root.getParentPage() != -1)
            throw new IllegalStateException("La raíz tiene parentPage=" + root.getParentPage()
                    + " en lugar de -1");
        Set<Integer> visitedNodes = new HashSet<>();
        List<Integer> leafPages = new ArrayList<>();
        List<Object> allKeys = new ArrayList<>();
        // Guarda el nivel en el que encontramos
        // la primera hoja.
        int[] leafLevel = {-1};
        validateNode(root, true, 0, visitedNodes, leafPages, allKeys, leafLevel);
        // Verificamos la lista enlazada de hojas.
        validateLeafChain(leafPages);
        // Verificamos que todas las claves estén
        // globalmente ordenadas.
        validateGlobalKeyOrder(allKeys);
    }

    // VALIDATE NODE =========================================================
    private void validateNode(BPlusTreeNode node, boolean isRoot, int level,
                              Set<Integer> visitedNodes, List<Integer> leafPages,
                              List<Object> allKeys, int[] leafLevel) throws IOException {
        int page = node.getPageNumber();
        // Un nodo no puede aparecer dos veces
        // dentro del mismo árbol.
        if (!visitedNodes.add(page))
            throw new IllegalStateException("Nodo visitado más de una vez: página " + page);

        System.out.println(
                "VALIDANDO nodo=" + page
                        + " parent=" + node.getParentPage()
                        + " root=" + isRoot
                        + " tipo=" + (node.isLeaf() ? "LEAF" : "INTERNAL")
        );
        // HOJA // -----------------------------------------------------
        if (node.isLeaf()) {
            validateLeaf(node, isRoot, level, leafPages, allKeys, leafLevel);
            return;
        }
        // NODO INTERNO -----------------------------------------------------
        validateInternal(node, isRoot);
        // Validamos todos sus hijos.
        for (int childPage : node.getChildren()) {
            BPlusTreeNode child = indexFile.readNode(childPage);
            // El hijo debe apuntar nuevamente
            // hacia este nodo.
            if (child.getParentPage() != node.getPageNumber()) {
                System.out.println(
                        "ERROR PARENT: nodo=" + node.getPageNumber()
                                + " parentActual=" + node.getParentPage()
                                + " parentEsperado=" + node.getPageNumber()
                );
                throw new IllegalStateException("Parent incorrecto: nodo " + childPage + " indica parent=" + child.getParentPage() + " pero debería ser " + node.getPageNumber());
            }
            validateNode(child, false, level + 1, visitedNodes, leafPages, allKeys, leafLevel);
        }
    }

    // VALIDATE INTERNAL NODE // =========================================================
    private void validateInternal(BPlusTreeNode node, boolean isRoot) throws IOException {
        int keyCount = node.getKeys().size();
        int childCount = node.getChildren().size();
        // Regla fundamental:
        //
        // children = keys + 1
        //
        if (childCount != keyCount + 1)
            throw new IllegalStateException("Nodo interno página " + node.getPageNumber()
                    + " inválido: keys=" + keyCount + ", children=" + childCount
                    + ". Debe cumplirse children = keys + 1");

        // Los nodos internos normales
        // deben cumplir el mínimo.
        if (!isRoot && childCount < MIN_INTERNAL_CHILDREN)
            throw new IllegalStateException("Underflow interno en página " + node.getPageNumber()
                    + ": children=" + childCount + ", mínimo=" + MIN_INTERNAL_CHILDREN);

        // Nunca debería existir un nodo
        // con más claves que el máximo.
        if (keyCount > MAX_INTERNAL_KEYS)
            throw new IllegalStateException("Overflow interno en página " + node.getPageNumber()
                    + ": keys=" + keyCount + ", máximo=" + MAX_INTERNAL_KEYS);

        // Una raíz interna necesita
        // al menos dos hijos.
        if (isRoot && childCount < 2)
            throw new IllegalStateException("La raíz interna tiene menos de 2 hijos: "
                    + childCount);

        // Las claves del nodo deben estar ordenadas.
        validateKeyOrder(node.getKeys(), "claves del nodo interno " + node.getPageNumber());
        /* * Las claves separadoras deben coincidir  con la primera clave de cada hijo derecho.
         * * children [A, B, C]
         * * keys [first(B), first(C)] */
        for (int i = 1; i < node.getChildren().size(); i++) {
            BPlusTreeNode child = indexFile.readNode(node.getChildren().get(i));
            Object expected = getFirstKey(child);
            Object actual = node.getKeys().get(i - 1);
            if (IndexKey.compare(actual, expected, keyType) != 0)
                throw new IllegalStateException("Separador incorrecto en nodo " + node.getPageNumber()
                        + ": keys[" + (i - 1) + "]=" + actual + " pero firstKey(child[" + i
                        + "])=" + expected);
        }
    }

    // VALIDATE LEAF // =========================================================
    private void validateLeaf(BPlusTreeNode leaf, boolean isRoot, int level, List<Integer> leafPages,
                              List<Object> allKeys, int[] leafLevel) {
        // Todas las hojas deben estar
        // exactamente en el mismo nivel.
        if (leafLevel[0] == -1) leafLevel[0] = level;
        else if (leafLevel[0] != level)
            throw new IllegalStateException("Las hojas no están en el mismo nivel: " + "hoja " +
                    leaf.getPageNumber() + " está en nivel " + level +
                    ", pero las anteriores están en nivel " + leafLevel[0]);

        // Una hoja que no es raíz debe
        // respetar el mínimo.
        if (!isRoot && leaf.getEntries().size() < MIN_LEAF_ENTRIES)
            throw new IllegalStateException("Underflow en hoja " + leaf.getPageNumber()
                    + ": entries=" + leaf.getEntries().size() + ", mínimo="
                    + MIN_LEAF_ENTRIES);

        // Una hoja no puede superar
        // su capacidad máxima.
        if (leaf.getEntries().size() > MAX_LEAF_ENTRIES)
            throw new IllegalStateException("Overflow en hoja " + leaf.getPageNumber() + ": entries=" + leaf.getEntries().size() + ", máximo=" + MAX_LEAF_ENTRIES);

        // Las claves de la hoja deben estar ordenadas.
        List<Object> keys = new ArrayList<>();
        for (IndexEntry entry : leaf.getEntries()) {
            keys.add(entry.getKey()); allKeys.add(entry.getKey());
        }
        validateKeyOrder( keys, "claves de hoja " + leaf.getPageNumber() );
        // Guardamos la hoja para validar
        // posteriormente la cadena nextPage.
        leafPages.add( leaf.getPageNumber() );
    }

    // VALIDATE KEY ORDER =========================================================
    private void validateKeyOrder( List<Object> keys, String description) {
        for (int i = 1; i < keys.size(); i++) {
            Object previous = keys.get(i - 1);
            Object current = keys.get(i);
            int comparison = IndexKey.compare( previous, current, keyType );
            if (comparison >= 0)
                throw new IllegalStateException( "Claves desordenadas en " + description + ": " + previous + " >= " + current );
        }
    }

    // VALIDATE GLOBAL KEY ORDER =========================================================
    private void validateGlobalKeyOrder( List<Object> keys) {
        for (int i = 1; i < keys.size(); i++) { Object previous = keys.get(i - 1);
            Object current = keys.get(i);
            int comparison = IndexKey.compare( previous, current, keyType );
            if (comparison >= 0)
                throw new IllegalStateException( "Orden global incorrecto: " + previous + " >= "+ current );
        }
    }

    // VALIDATE LEAF CHAIN =========================================================
    private void validateLeafChain( List<Integer> treeLeafPages) throws IOException {
        if (treeLeafPages.isEmpty())
            throw new IllegalStateException( "El árbol no contiene ninguna hoja" );
        /* * Buscamos la primera hoja.
        * * No podemos asumir que la primera página física sea la primera hoja.
        * * Buscamos una hoja cuyo parent no tenga * otro hijo a su izquierda.
        */
        int firstLeafPage = treeLeafPages.get(0);
        for (int page : treeLeafPages) {
            BPlusTreeNode leaf = indexFile.readNode(page);
            if (leaf.getParentPage() == -1) {
                firstLeafPage = page;
                break;
            }
            BPlusTreeNode parent = indexFile.readNode( leaf.getParentPage());
            int position = parent.getChildren().indexOf(page);
            if (position == 0)
                firstLeafPage = page; break;
        }
        Set<Integer> visitedLeaves = new HashSet<>();
        int currentPage = firstLeafPage;
        Object previousKey = null;
        while (currentPage != -1) {
            // Detectamos ciclos.
            if (!visitedLeaves.add(currentPage))
                throw new IllegalStateException( "La cadena de hojas contiene un ciclo. "
                        + "Página repetida: " + currentPage );
            BPlusTreeNode leaf = indexFile.readNode( currentPage );
            // Esta página debe ser una hoja.
            if (!leaf.isLeaf())
                throw new IllegalStateException( "nextPage apunta a un nodo interno: "
                        + currentPage );
            for (IndexEntry entry : leaf.getEntries()) {
                Object currentKey = entry.getKey();
                if (previousKey != null) {
                    int comparison = IndexKey.compare( previousKey, currentKey, keyType );
                    if (comparison >= 0)
                        throw new IllegalStateException( "Orden incorrecto en cadena de hojas: "
                                + previousKey + " >= " + currentKey );
                }
                previousKey = currentKey;
            }
            currentPage = leaf.getNextPage();
        }
        /* * La cantidad de hojas alcanzadas mediante nextPage debe coincidir con la cantidad de
        hojas encontradas * desde la raíz. */
        if ( visitedLeaves.size() != treeLeafPages.size() )
            throw new IllegalStateException( "Cadena de hojas incompleta: "
                    + "desde el árbol se encontraron " + treeLeafPages.size()
                    + " hojas, pero nextPage recorrió " + visitedLeaves.size() );
        // Todas las hojas del árbol deben aparecer exactamente una vez.
        if ( !visitedLeaves.containsAll( treeLeafPages ) )
            throw new IllegalStateException( "La cadena nextPage no contiene "
                    + "todas las hojas del árbol" );
    }
}