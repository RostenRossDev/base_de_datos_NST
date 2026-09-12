package com.rostendev.database.fkIndex;

import java.util.ArrayList;
import java.util.List;

public class RelationNode<P, F> {
    public static final int LEAF = 1;
    public static final int INTERNAL = 2;

    private final int pageNumber;
    private final int nodeType;

    private int parentPage;
    private int nextPage;

    private final List<RelationEntry<P, F>> entries;
    private final List<P> keys;
    private final List<Integer> children;

    public RelationNode(int pageNumber, int nodeType) {
        this.pageNumber = pageNumber;
        this.nodeType = nodeType;
        this.parentPage = -1;
        this.nextPage = -1;

        this.entries = new ArrayList<>();
        this.keys = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public boolean isLeaf() {
        return nodeType == LEAF;
    }

    public boolean isInternal(){
        return nodeType == INTERNAL;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getNodeType() {
        return nodeType;
    }

    public int getParentPage() {
        return parentPage;
    }

    public void setParentPage(int parentPage) {
        this.parentPage = parentPage;
    }

    public int getNextPage() {
        return nextPage;
    }

    public void setNextPage(int nextPage) {
        this.nextPage = nextPage;
    }

    public List<RelationEntry<P, F>> getEntries() {
        return entries;
    }

    public List<P> getKeys() {
        return keys;
    }

    public List<Integer> getChildren() {
        return children;
    }
}
