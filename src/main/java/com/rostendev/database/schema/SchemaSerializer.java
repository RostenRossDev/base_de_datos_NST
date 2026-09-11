package com.rostendev.database.schema;

import java.io.*;

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

    public Schema read(String path) throws IOException {
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {

            String tableName = in.readUTF();
            Schema schema = new Schema(tableName);
            int columnCount = in.readInt();
            for (int i = 0; i < columnCount; i++) {
                String name = in.readUTF();
                DataType type = DataType.valueOf(in.readUTF());
                Integer length = null;
                boolean hasLength = in.readBoolean();
                if (hasLength) {
                    length = in.readInt();
                }

                boolean nullable = in.readBoolean();
                boolean primaryKey = in.readBoolean();
                boolean foreingKey = in.readBoolean();
                boolean unique = in.readBoolean();

                ColumnDefinition column = new ColumnDefinition(name, type, length, nullable, primaryKey, foreingKey, unique);
                schema.addColumn(column);
            }
            return  schema;
        }
    }
}
