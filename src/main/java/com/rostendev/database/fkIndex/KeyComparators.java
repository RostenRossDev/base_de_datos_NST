package com.rostendev.database.fkIndex;

import com.rostendev.database.schema.DataType;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class KeyComparators {

    private KeyComparators(){
    }

    public static <T> KeyComparator<T> from(DataType type) {
        return switch (type) {

            case BYTE ->
                    (a, b) -> Byte.compare((Byte) a,(Byte) b);

            case SHORT ->
                    (a, b) -> Short.compare((Short) a,(Short) b);

            case INT ->
                    (a, b) -> Integer.compare((Integer) a,(Integer) b);

            case LONG ->
                    (a, b) -> Long.compare((Long) a,(Long) b);

            case DOUBLE ->
                    (a, b) -> Double.compare((Double) a,(Double) b);

            case BOOLEAN ->
                    (a, b) -> Boolean.compare((Boolean) a,(Boolean) b);

            case STRING ->
                    (a, b) -> ((String) a).compareTo((String) b);

            case BIGINT ->
                    (a, b) ->((BigInteger) a).compareTo((BigInteger) b);

            case BIGDECIMAL ->
                    (a, b) ->((BigDecimal) a).compareTo((BigDecimal) b);
        };
    }
}
