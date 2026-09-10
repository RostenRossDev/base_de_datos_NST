package com.rostendev.database.schema;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SchemaSerializer {

    public void write(Schema schema, String path) throws IOException {
        try(DataOutputStream out =
                    new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))){
            out.writeUTF(schema.getTableName());
            out.writeInt(schema.getColumns().size());
            for (ColumnDefinition column : schema.getColumns()) {
                out.writeUTF(column.getName());
                out.writeUTF(column.getType().name());
                if (column.getLength() == null) out.writeBoolean(false);
                else {
                    out.writeBoolean(true);
                    out.writeInt(column.getLength());
                }
                out.writeBoolean(column.isNullable());
                out.writeBoolean(column.isPrimaryKey());
                out.writeBoolean(column.isForeignKey());
                out.writeBoolean(column.isUnique());
            }
        }
    }
}
