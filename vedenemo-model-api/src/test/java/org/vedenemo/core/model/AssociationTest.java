package org.vedenemo.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class AssociationTest {

    private static final ModelVersion VERSION = ModelVersion.parse("1.0.0");

    @Test
    void ownershipAssociationExposesAssociationData() {
        OwnershipAssociation association = new OwnershipAssociation(
                "Customer_Orders",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*"),
                VERSION
        );

        assertEquals("Customer_Orders", association.azName());
        assertEquals("orders", association.visName());
        assertEquals("Customer", association.sourceEntityAzName());
        assertEquals("Order", association.targetEntityAzName());
        assertEquals(Cardinality.parse("0..*"), association.cardinality());
        assertEquals(AssociationKind.OWNERSHIP, association.kind());
        assertEquals(VERSION, association.activeSince());
    }

    @Test
    void referenceAssociationExposesAssociationData() {
        ReferenceAssociation association = new ReferenceAssociation(
                "Order_Customer",
                "customer",
                "Order",
                "Customer",
                Cardinality.parse("1"),
                VERSION
        );

        assertEquals(AssociationKind.REFERENCE, association.kind());
        assertEquals("1", association.cardinality().toString());
    }

    @Test
    void rejectsInvalidAssociationData() {
        assertThrows(IllegalArgumentException.class, () -> new OwnershipAssociation(
                "1Invalid",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*"),
                VERSION
        ));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceAssociation(
                "Valid",
                " ",
                "Customer",
                "Order",
                Cardinality.parse("1"),
                VERSION
        ));
        assertThrows(NullPointerException.class, () -> new ReferenceAssociation(
                "Valid",
                "customer",
                "Order",
                "Customer",
                null,
                VERSION
        ));
    }
}
