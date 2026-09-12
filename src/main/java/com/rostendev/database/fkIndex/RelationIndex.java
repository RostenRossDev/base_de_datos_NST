package com.rostendev.database.fkIndex;

import com.rostendev.database.schema.DataType;
import com.rostendev.database.storage.RecordPointer;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RelationIndex<P, F> implements AutoCloseable{
    private static final int MAX_LEAF_ENTRIES = 3;
    private static final int MAX_INTERNAL_KEYS = 3;

    private final RelationIndexFile indexFile;
    private final KeyComparator<P> parentComparator;
    private final KeyComparator<F> foreignComparator;

    public RelationIndex(Path path, DataType pkType, DataType fkType) throws IOException {
        this.indexFile = new RelationIndexFile(path, pkType, fkType);
        this.parentComparator = KeyComparators.from(pkType);
        this.foreignComparator = KeyComparators.from(fkType);
    }

    public void insert(P parentKey, F foreignKey, RecordPointer pointer) throws IOException {
        validateKeys(parentKey, foreignKey);
        if (pointer == null) throw new IllegalArgumentException("pointer no puede ser null");
        RelationNode<P,F> leaf =  findLeaf(parentKey);
        if (contains(leaf, parentKey, foreignKey))
            throw new IllegalArgumentException("La relacion ya existe: parentKey=" + parentKey
            +", foreignKey = " + foreignKey);
        RelationEntry<P,F> entry = new RelationEntry<>(parentKey, foreignKey, pointer);
        insertSorted(leaf, entry);
        if (leaf.getEntries().size() <= MAX_LEAF_ENTRIES) {
            indexFile.writeNode(leaf);
            return;
        }
        splitLeaf(leaf);
    }

    public boolean hasReference(P parentKey) throws IOException {
        if (parentKey == null) return false;
        RelationNode<P,F> leaf = findFirstLeaf(parentKey);
        while(leaf != null) {
            for (RelationEntry<P,F> entry : leaf.getEntries()) {
                int comparision = parentComparator.compare(entry.getParentKey(), parentKey);
                if (comparision == 0) return true;
                if (comparision > 0) return false;
            }
            if (leaf.getNextPage() == -1) return false;
            leaf = indexFile.readNode(leaf.getNextPage());
        }
        return false;
    }

    public List<RelationEntry<P,F>> find(P parentKey) throws IOException {
        if (parentKey == null) throw new IllegalArgumentException("parentKey no puede ser null");
        List<RelationEntry<P,F>> result = new ArrayList<>();
        RelationNode<P,F> leaf = findFirstLeaf(parentKey);
        while (leaf != null) {
            boolean greaterThanParent = false;
            for (RelationEntry<P,F> entry : leaf.getEntries()) {
                int comparison = parentComparator.compare(entry.getParentKey(), parentKey);
                if (comparison == 0) {
                    result.add(entry);
                } else if (comparison > 0) {
                    greaterThanParent = true;
                    break;
                }
            }
            if (greaterThanParent) break;
            if (leaf.getNextPage() == -1) break;
            leaf = indexFile.readNode(leaf.getNextPage());
        }
        return  result;
    }

    public void delete(P parentKey, F foreignKey) throws IOException {
        validateKeys(parentKey, foreignKey);
        RelationNode leaf = findFirstLeaf(parentKey);
        while(leaf != null) {
            List<RelationEntry<P,F>> entries = leaf.getEntries();
            for (int i = 0; i < entries.size(); i++){
                RelationEntry<P,F> entry = entries.get(i);
                int parentComparison = parentComparator.compare(entry.getParentKey(), parentKey);
                if (parentComparison > 0) return;
                if (parentComparison == 0 && foreignComparator.compare(entry.getForeignKey(), foreignKey) == 0) {
                    entries.remove(i);
                    indexFile.writeNode(leaf);
                    return;
                }
            }
            if (leaf.getNextPage() == -1) return;
            leaf = indexFile.readNode(leaf.getNextPage());
        }
    }

    private RelationNode<P,F> findLeaf(P parentKey) throws IOException {
        RelationNode<P,F> node = indexFile.readNode(indexFile.getRootPage());
        while (node.isInternal()) {
            int childIndex = 0;

            /*Buscamos el primer hijo cuyo separador sea mayor que parentKey.
             *
             * Esto es importante porque un mismo parentKey puede existir en varias hojas.*/
            while (childIndex < node.getKeys().size() &&
            parentComparator.compare(parentKey, node.getKeys().get(childIndex)) >= 0) {
                childIndex++;
            }
            int childPage = node.getChildren().get(childIndex);
            node = indexFile.readNode(childPage);
        }
        return node;
    }

    private RelationNode<P, F> findFirstLeaf(P parentKey) throws IOException{
        RelationNode<P, F> node = indexFile.readNode(indexFile.getRootPage());
        while (node.isInternal()) {
            int childIndex = 0;
            /* Para buscar el primer lugar donde puede aparecer parentKey usamos > y no >=.*/
            while (childIndex < node.getKeys().size() &&
            parentComparator.compare(parentKey, node.getKeys().get(childIndex)) > 0) {
                childIndex++;
            }
            node = indexFile.readNode(node.getChildren().get(childIndex));
        }
        return node;
    }

    private boolean contains(RelationNode<P, F> node, P parentKey, F foreignKey){
        for (RelationEntry<P,F> entry : node.getEntries()){
            if (parentComparator.compare(entry.getParentKey(), parentKey) == 0
                    && foreignComparator.compare(entry.getForeignKey(), foreignKey) == 0) {
                return true;
            }
        }
        return false;
    }

    private void insertSorted(RelationNode<P,F> node, RelationEntry<P,F> newEntry) {
        List<RelationEntry<P,F>> entries = node.getEntries();
        int position = 0;
        while (position < entries.size()){
            RelationEntry<P,F> current = entries.get(position);
            int parentComparison = parentComparator.compare(newEntry.getParentKey(), current.getParentKey());
            if (parentComparison < 0) break;
            if (parentComparison == 0) {
                int foreignComparison = foreignComparator.compare(newEntry.getForeignKey(), current.getForeignKey());
                if (foreignComparison <0) break;
            }
            position++;
        }
        entries.add(position, newEntry);
    }

    private void splitLeaf(RelationNode<P, F> leaf) throws IOException {
        int newPage = indexFile.allocatePage();
        RelationNode<P, F> rigth = new RelationNode<>(newPage, RelationNode.LEAF);
        rigth.setParentPage(leaf.getParentPage());
        rigth.setNextPage(leaf.getNextPage());
        leaf.setNextPage(newPage);
        int middle = leaf.getEntries().size() / 2;
        while (leaf.getEntries().size() > middle) {
            rigth.getEntries().add(leaf.getEntries().remove(middle));
        }
        indexFile.writeNode(leaf);
        indexFile.writeNode(rigth);
        P separator = rigth.getEntries().getFirst().getParentKey();
        if (leaf.getParentPage() == -1) {
            createNewRoot(leaf, rigth, separator);
            return;
        }
        RelationNode<P, F> parent = indexFile.readNode(leaf.getParentPage());
        insertIntoParent(parent, leaf, rigth, separator);
    }

    private void createNewRoot(RelationNode<P, F> left, RelationNode<P, F> rigth, P separator) throws IOException {
        int rootPage = indexFile.allocatePage();
        RelationNode<P,F> root = new RelationNode<>(rootPage, RelationNode.INTERNAL);
        root.getKeys().add(separator);
        root.getChildren().add(left.getPageNumber());
        root.getChildren().add(rigth.getPageNumber());
        left.setParentPage(rootPage);
        rigth.setParentPage(rootPage);
        indexFile.writeNode(left);
        indexFile.writeNode(rigth);
        indexFile.writeNode(root);
        indexFile.setRootPage(rootPage);
    }

    private void insertIntoParent(RelationNode<P,F> parent,RelationNode<P,F> left,RelationNode<P,F> right,P separator) throws IOException {
        int leftIndex = parent.getChildren().indexOf(left.getPageNumber());
        if (leftIndex == -1) throw new IllegalArgumentException("El hijo no existe en el padre");
        parent.getKeys().add(leftIndex, separator);
        parent.getChildren().add(leftIndex + 1, right.getParentPage());
        if (parent.getKeys().size() <= MAX_INTERNAL_KEYS) {
            indexFile.writeNode(parent);
            return;
        }
        splitInternal(parent);
    }

    private void splitInternal(RelationNode<P,F> node) throws IOException {
        int middle = node.getKeys().size() / 2;
        P separator = node.getKeys().get(middle);
        int newPage = indexFile.allocatePage();
        RelationNode<P,F> right = new RelationNode<>(newPage, RelationNode.INTERNAL);
        right.setParentPage(node.getParentPage());

        /* Las claves posteriores al separador  pasan al nuevo nodo.*/
        for (int i = middle +1; i < node.getKeys().size();) {
            right.getKeys().add(node.getKeys().remove(i));
        }

         /* Un nodo interno tiene: children = keys + 1*/
        for (int i = middle + 1; i < node.getChildren().size();) {
            right.getChildren().add(node.getChildren().remove(i));
        }

        /* El separador sube al padre, por lo tanto desaparece del nodo izquierdo.*/
        node.getKeys().remove(middle);
        for (Integer child : right.getChildren()){
            RelationNode<P,F> childNode = indexFile.readNode(child);
            childNode.setParentPage(newPage);
            indexFile.writeNode(childNode);
        }
        indexFile.writeNode(node);
        indexFile.writeNode(right);
        if (node.getParentPage() == -1) {
            createNewRoot(node, right, separator);
            return;
        }
        RelationNode<P,F> parent = indexFile.readNode(node.getParentPage());
        insertIntoParent(parent, node, right, separator);
    }

//    private int compareParent(Object a, Object b) {
//        return compare(a, b, indexFile.getParentKeyType());
//    }
//
//    private int compareForeignKey(Object a, Object b) {
//        return compare(a, b, indexFile.getForeignKeyType());
//    }
//
//    //@SuppressWarnings({"unchecked", "rawtypes"})
//    private int compare(Object a,Object b,DataType type) {
//        if (a == b) return 0;
//        if (a == null) return -1;
//        if (b == null) return 1;
//        return switch (type) {
//            case BYTE -> Byte.compare((Byte) a,(Byte) b);
//            case SHORT -> Short.compare((Short) a,(Short) b);
//            case INT -> Integer.compare((Integer) a,(Integer) b);
//            case LONG -> Long.compare((Long) a,(Long) b);
//            case DOUBLE -> Double.compare((Double) a,(Double) b);
//            case BOOLEAN -> Boolean.compare((Boolean) a,(Boolean) b);
//            case STRING -> ((String) a).compareTo((String) b);
//            case BIGINT -> ((java.math.BigInteger) a).compareTo((java.math.BigInteger) b);
//            case BIGDECIMAL -> ((java.math.BigDecimal) a).compareTo((java.math.BigDecimal) b);
//        };
//    }

    private void validateKeys(Object parentKey, Object foreignKey) {
        if (parentKey == null) throw new IllegalArgumentException("parentKey no puede ser null");
        if (foreignKey == null) throw new IllegalArgumentException("foreignKey no puede ser null");
    }

    @Override
    public void close() throws Exception {
        indexFile.close();
    }
}
