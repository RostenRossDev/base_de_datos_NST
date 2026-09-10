package com.rostendev.database.schema;

import java.io.IOException;

public class SchemaFile {
    private final String path;
    private final SchemaSerializer serializer;

    public SchemaFile(String path) {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("path no puede ser nulo o vacio");

        this.path = path;
        this.serializer = new SchemaSerializer();
    }

    public void write(Schema schema) throws IOException {
        if (schema == null)
            throw new IllegalArgumentException("schema no puede ser null");

        serializer.write(schema, path);
    }
}
