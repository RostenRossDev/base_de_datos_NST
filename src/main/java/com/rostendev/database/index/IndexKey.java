package com.rostendev.database.index;

import com.rostendev.database.schema.DataType;

import javax.xml.crypto.Data;
import java.math.BigDecimal;
import java.math.BigInteger;

public class IndexKey {

    private IndexKey(){
    }

    public static void validate(Object key, DataType type){
        if (key == null) throw new IllegalArgumentException("La clave no puede ser null");
        switch (type){
            case BYTE:
                require(key, Byte.class, type);
                break;
            case SHORT:
                require(key, Short.class, type);
                break;
            case INT:
                require(key, Integer.class, type);
                break;
            case LONG:
                require(key, Long.class, type);
                break;
            case STRING:
                require(key, String.class, type);
                break;
            case BIGINT:
                require(key, BigInteger.class, type);
                break;
            default:
                throw new IllegalArgumentException("Tipo no soportado como clave" + type);
        }
    }

    public static int compare(Object left, Object right, DataType type){
        validate(left, type);
        validate(right, type);

        switch (type) {
            case BYTE:
                return Byte.compare((Byte) left, (Byte) right);
            case SHORT:
                return Short.compare((Short) left,(Short) right);
            case INT:
                return Integer.compare((Integer) left,(Integer) right);
            case LONG:
                return Long.compare((Long) left,(Long) right);
            case STRING:
                return ((String) left).compareTo((String) right);
            case BIGINT:
                return ((BigInteger) left).compareTo((BigInteger) right);
            default:
                throw new IllegalArgumentException("Tipo no soportado como clave: "+ type);
        }
    }

    private static void require(Object value, Class<?> expected, DataType type) {
        if (!expected.isInstance(value)) throw new IllegalArgumentException("Clave incopatible con "
                + type + ". Se esperaba " + expected.getSimpleName()+ " pero se recibio " + value.getClass().getSimpleName());
    }
}
