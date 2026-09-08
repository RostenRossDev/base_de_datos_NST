package com.rostendev.database.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BPlusTreeNode {
    public static final int LEAF = 1;
    public static final int INTERNAL = 2;

    private int pageNumber;
    private int nodeType;
    private int keyCounter;
    private int parentPage;
    private int nextPage;

    // Para hojas
    private final List<IndexEntry> entries;

    // Para nodos internos
    private final List<Object> keys;
    private final List<Integer> children;

    public BPlusTreeNode(int pageNumber, int nodeType) {
        this.pageNumber = pageNumber;
        this.nodeType = nodeType;

        this.keyCounter = 0;
        this.parentPage = -1;
        this.nextPage = -1;

        this.entries = new ArrayList<>();
        this.keys = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public boolean isLeaf(){
        return nodeType == LEAF;
    }

    public boolean isInternal(){
        return  nodeType == INTERNAL;
    }

    public void setPageNumber(int pageNumber){
        this.pageNumber = pageNumber;
    }

    public int getNodeType(){
        return nodeType;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setNodeType(int nodeType) {
        this.nodeType = nodeType;
    }

    public int getKeyCounter() {
        return keyCounter;
    }

    public void setKeyCounter(int keyCounter) {
        this.keyCounter = keyCounter;
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

    public List<IndexEntry> getEntries() {
        return entries;
    }

    public List<Object> getKeys() {
        return keys;
    }

    public List<Integer>  getChildren() {
        return children;
    }

    public void updateKeyCount(){
        if (isLeaf()){
            keyCounter = entries.size();
        } else {
            keyCounter = keys.size();
        }
    }

    @Override
    public String toString() {

        if (isLeaf()) {

            return "Leaf{" +
                    "page=" + pageNumber +
                    ", parent=" + parentPage +
                    ", next=" + nextPage +
                    ", entries=" + entries +
                    '}';
        }

        return "Internal{" +
                "page=" + pageNumber +
                ", parent=" + parentPage +
                ", keys=" + keys +
                ", children=" + children +
                '}';
    }
}
