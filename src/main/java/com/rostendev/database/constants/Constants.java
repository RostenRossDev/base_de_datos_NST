package com.rostendev.database.constants;

public class Constants {
    //database
    public static final String ROOT_DIRECTORY = "database";

    //SOTRAGE FILE CONSTANTS
    public static final int INDEX_PAGE_SIZE = 4096;
    public static final int PAGE_SIZE = 4096;
    public static final int HEADER_SIZE = 8;
    public static final int SLOT_SIZE = 8;

    //B TREE INDEX CONSTANTS
    static final int NODE_HEADER_SIZE = 16;

    static final int NODE_TYPE_LEAF = 1;

    static final int NODE_TYPE_INTERNAL = 2;

    static final int LEAF_ENTRY_SIZE = 12;

    static final int INTERNAL_ENTRY_SIZE = 8;

    static final int MAX_LEAF_ENTRIES =(INDEX_PAGE_SIZE - NODE_HEADER_SIZE)/LEAF_ENTRY_SIZE;

    static final int MAX_INTERNAL_KEYS = (INDEX_PAGE_SIZE - NODE_HEADER_SIZE - 4)/INTERNAL_ENTRY_SIZE;
}
