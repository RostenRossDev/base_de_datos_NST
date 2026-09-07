package com.rostendev.database.records;

import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Record {
    private final Schema schema;
    private final Object[] values;

    public Record(Schema schema) {
        this.schema = schema;
        this.values = new Object[schema.getColumns().size()];
    }

    public void set(int columnIndex, Object value) {
        ColumnDefinition column = schema.getColumns().get(columnIndex);
        validateType(column, value);
        values[columnIndex] = value;
    }

    //PRIVATE
    private void validateType(ColumnDefinition column, Object value) {
        if (value == null) {
            if (!column.isNullable()) throw  new IllegalArgumentException("Columna '" + column.getName()+"' no puede ser nulo");
            return;
        }

        switch (column.getType()){
            case BYTE:
                if (!(value instanceof Byte)) throw new IllegalArgumentException("Columna '"+column.getName() + "' debe ser un BYTE");
                break;
            case SHORT:
                if (!(value instanceof Short)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un SHORT");
                break;
            case INT:
                if (!(value instanceof Integer)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un INTEGER");
                break;
            case LONG:
                if (!(value instanceof Long)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un LONG");
                break;
            case DOUBLE:
                if (!(value instanceof Double)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un DOUBLE");
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un BOOLEAN");
                break;
            case STRING:
                if (!(value instanceof String)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un STRING");
                break;
            case BIGINT:
                if (!(value instanceof BigInteger)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un BIGINT");
                break;
            case BIGDECIMAL:
                if (!(value instanceof BigDecimal)) throw new IllegalArgumentException("Columna '"+column.getName()+"' debe ser un BIGDECIMAL");
                break;
        }
    }

    //GETTERS
    public Object get(int columnIndex) {
        return values[columnIndex];
    }

    public Object[] getValues() {
        return values;
    }

    public Schema getSchema() {
        return schema;
    }
}
