package com.rostendev.database.index;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BPlusTree {
    private final IndexFile indexFile;
    private static final int MAX_LEAF_ENTRIES = 3;
    private static final int MAX_INTERNAL_KEYS = 3;

    public BPlusTree(Path indexPath) throws IOException {
        this.indexFile = new IndexFile(indexPath);
    }

    public void insert(int id, int dataPage, int dataSlot) throws IOException {
        IndexEntry entry = new IndexEntry(id, dataPage, dataSlot);

        //1. Empezamos en la raiz
        BPlusTreeNode node = indexFile.readNode(indexFile.getRootPage());

        //2. Bjamos hasta encontrar una hoja.
        while(node.isInternal()){
            int childPage = findChildPage(node, id);
            node = indexFile.readNode(childPage);
        }
        //3. Ahora "node" es la hoja correcta
        insertIntoLeaf(node, entry);
    }

    public IndexEntry search(int id) throws IOException {
        BPlusTreeNode node = indexFile.readNode(indexFile.getRootPage());

        //bajamos desde la raiz hasta uan hoja.
        while(node.isInternal()){
            int childPage = findChildPage(node, id);
            node = indexFile.readNode(childPage);
        }
        //Estamos en una hora
        for (IndexEntry entry : node.getEntries()){
            //encontramos y retornamos el entry
            if (entry.getId() == id) return entry;

            //Como la hoja esta ordenada, si ya pasamos el id podemos terminar.
            if (entry.getId() > id) return null;
        }

        return null;
    }

    private int findChildPage(BPlusTreeNode node, int id){
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
        List<Integer> keys = node.getKeys();
        List<Integer> children = node.getChildren();
        int position = 0;
        while(position < keys.size() && id >= keys.get(position)){
            position++;
        }

        return children.get(position);
    }
    private void insertIntoLeaf(BPlusTreeNode leaf, IndexEntry entry) throws IOException {
        int position = 0;
        while(position < leaf.getEntries().size()
                && leaf.getEntries().get(position).getId() < entry.getId()){
            position++;
        }

        // No permitimos IDs duplicados
        if (position < leaf.getEntries().size()
            && leaf.getEntries().get(position).getId() == entry.getId()) {
            throw new IllegalArgumentException("El id ya existe: " + entry.getId());
        }

        leaf.getEntries().add(position, entry);

        //todavia entra en la hoja
        if (leaf.getEntries().size() <= MAX_LEAF_ENTRIES) {
            indexFile.writeNode(leaf);
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

        /* * Las hojas forman una lista.
        *  * oldLeaf -> newLeaf -> antiguo siguiente */

        newLeaf.setNextPage(oldLeaf.getNextPage());
        oldLeaf.setNextPage(newLeafPage);
        /* * La hoja antigua deja de ser root.
        /* * La primera clave de la nueva hoja
        * será el separador que subiremos al padre. */
        int separator = newLeaf.getEntries().get(0).getId();
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
            insertIntoParent( oldLeaf, newLeaf, separator );
        }
    }

    private void createNewRoot(BPlusTreeNode leftLeaf, BPlusTreeNode rigthLeaf, int separator) throws IOException{
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

    private void insertIntoParent(BPlusTreeNode leftChild, BPlusTreeNode rigthChild, int separator) throws IOException {
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

        List<Integer> keys = oldNode.getKeys();
        List<Integer> children = oldNode.getChildren();

        int middle = keys.size() / 2;
        int separator = keys.get(middle);
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
            keys.remove(keys.size() -1);
        }

        while (children.size() > middle + 1) {
            children.remove(children.size() -1);
        }

        newNode.setParentPage(oldNode.getParentPage());

        /* * Todos los hijos que pasaron al nuevo nodo ahora tienen otro padre. */
        for (int childPage: newNode.getChildren()){
            BPlusTreeNode child = indexFile.readNode(childPage);
            child.setParentPage(newPage);
            indexFile.writeNode(child);
        }

        indexFile.writeNode(oldNode);
        indexFile.writeNode(newNode);

        /* * ¿El nodo dividido era la raíz? */
        if (oldNode.getParentPage() == -1) {
            createNewRoot(oldNode, newNode, separator);
        } else {
            insertIntoParent(oldNode, newNode, separator);
        }
    }
}
