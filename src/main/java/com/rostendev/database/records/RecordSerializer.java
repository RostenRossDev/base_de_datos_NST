package com.rostendev.database.records;

import com.rostendev.database.schema.ColumnDefinition;
import com.rostendev.database.schema.Schema;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class RecordSerializer {

    public byte[] serialize(Record record) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteStream);

        Schema schema = record.getSchema();
        int columnCount = schema.getColumns().size();
        int bitmapSize = (columnCount + 7) / 8;
        byte[] nullBitmap = createNullBitmap(record);

        //NULL BITMAP
        out.write(nullBitmap);

        //DATA
        for (int i = 0; i < schema.getColumns().size(); i++){
            ColumnDefinition column = schema.getColumns().get(i);
            Object value = record.get(i);
            if (value == null) { //Si es esta marcado como nullo saltamos
                continue;
            }

            writeValue(out, column, value);
        }
        out.flush();
        return byteStream.toByteArray();
    }

    public Record deserialize(byte[] data, Schema schema) throws IOException{
        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(byteStream);
        Record record = new Record(schema);
        int columnCount = schema.getColumns().size();
        int bitmapSize = (columnCount + 7) / 8;
        byte[] nullBitmap = new byte[bitmapSize];
        in.readFully(nullBitmap);

        for (int i=0; i < schema.getColumns().size(); i++){
            ColumnDefinition column = schema.getColumns().get(i);
            if (isNull(nullBitmap, i)) {
                record.set(i, null);
                continue;
            }
            Object value = readValue(in, column);
            record.set(i, value);
        }
        return record;
    }

    private void writeValue(DataOutputStream out, ColumnDefinition column, Object value) throws IOException {

        switch (column.getType()) {
            case BYTE:
                out.writeByte((Byte) value);
                break;
            case SHORT:
                out.writeShort((Short) value);
                break;
            case INT:
                out.writeInt((Integer) value);
                break;
            case LONG:
                out.writeLong((Long) value);
                break;
            case DOUBLE:
                out.writeDouble((Double) value);
                break;
            case BOOLEAN:
                out.writeBoolean((Boolean) value);
                break;
            case STRING:
                writeString(out, (String) value);
                break;
            case BIGDECIMAL:
                writeBigDecimal(out, (BigDecimal) value);
                break;
            case BIGINT:
                writeBigInteger(out, (BigInteger) value);
                break;
            default:
                throw new IllegalArgumentException("Tipo no soportado: " + column.getType());
        }
    }

    private Object readValue(DataInputStream in, ColumnDefinition column) throws IOException {

        switch (column.getType()){
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case DOUBLE:
                return in.readDouble();
            case BOOLEAN:
                return in.readBoolean();
            case STRING:
                return readString(in);
            case BIGINT:
                return readBigInteger(in);
            case BIGDECIMAL:
                return readBigDecimal(in);
            default: throw  new IllegalArgumentException("Tipo no soportado: " + column.getType());
        }
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeBigInteger(DataOutputStream out, BigInteger value) throws  IOException {
        byte[] bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private BigInteger readBigInteger(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new BigInteger(bytes);
    }

    private void writeBigDecimal(DataOutputStream out, BigDecimal value) throws IOException {
        writeBigInteger(out, value.unscaledValue());
        out.writeInt(value.scale());
    }

    private BigDecimal readBigDecimal(DataInputStream in) throws IOException {
        BigInteger unscaledValue = readBigInteger(in);
        int scale = in.readInt();
        return new BigDecimal(unscaledValue, scale);
    }

    private byte[] createNullBitmap(Record record) {
        int columnCount = record.getSchema().getColumns().size();
        int bitmanSize = (columnCount + 7) / 8;
        byte[] bitmap = new byte[bitmanSize];

        for (int i = 0; i < columnCount; i++) {
            if (record.get(i) == null) {
                int byteIndex = i / 8;
                int biteIndex =  i % 8;
                bitmap[byteIndex] |= (1 << biteIndex);
            }
        }
        return bitmap;
    }

    private boolean isNull(byte[] bitmap, int columnIndex){
        int byteIndex = columnIndex / 8;
        int bitIndex = columnIndex % 8;
        return (bitmap[byteIndex] & (1 << bitIndex)) != 0;
    }
}
