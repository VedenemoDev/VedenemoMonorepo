package org.vedenemo.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class CardinalityTest {

    @Test
    void parsesExactBound() {
        Cardinality cardinality = Cardinality.parse("1");

        assertEquals(1, cardinality.lowerBound());
        assertEquals(OptionalInt.of(1), cardinality.upperBound());
        assertFalse(cardinality.isUpperUnbounded());
        assertEquals("1", cardinality.toString());
    }

    @Test
    void parsesOptionalBound() {
        Cardinality cardinality = Cardinality.parse("0..1");

        assertEquals(0, cardinality.lowerBound());
        assertEquals(OptionalInt.of(1), cardinality.upperBound());
        assertEquals("0..1", cardinality.toString());
    }

    @Test
    void parsesUnboundedRanges() {
        assertEquals("0..*", Cardinality.parse("0..*").toString());
        assertEquals("1..*", Cardinality.parse("1..*").toString());
    }

    @Test
    void parsesWildcardShorthandAsZeroToMany() {
        Cardinality cardinality = Cardinality.parse("*");

        assertEquals(0, cardinality.lowerBound());
        assertEquals(OptionalInt.empty(), cardinality.upperBound());
        assertTrue(cardinality.isUpperUnbounded());
        assertEquals("0..*", cardinality.toString());
    }

    @Test
    void parsesBoundedRange() {
        Cardinality cardinality = Cardinality.parse("2..5");

        assertEquals(2, cardinality.lowerBound());
        assertEquals(OptionalInt.of(5), cardinality.upperBound());
        assertEquals("2..5", cardinality.toString());
    }

    @Test
    void normalizesOneToOneRangeAsOne() {
        assertEquals("1", Cardinality.parse("1..1").toString());
    }

    @Test
    void rejectsZeroToZero() {
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("0..0"));
    }

    @Test
    void rejectsMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("-1"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("1..0"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("1...*"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("1..2..3"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("many"));
    }

    @Test
    void rejectsMissingText() {
        assertThrows(NullPointerException.class, () -> Cardinality.parse(null));
    }
}
