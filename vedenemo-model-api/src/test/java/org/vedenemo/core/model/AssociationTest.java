package org.vedenemo.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

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
        assertEquals(Optional.empty(), association.retiredSince());
    }

    @Test
    void acceptsRetiredSinceMetadata() {
        ModelVersion retiredSince = ModelVersion.parse("3.0.0");
        OwnershipAssociation association = new OwnershipAssociation(
                "Customer_Orders",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*"),
                VERSION,
                null,
                retiredSince
        );

        assertEquals(Optional.of(retiredSince), association.retiredSince());
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
    void relationAssociationExposesTwoNamedEnds() {
        RelationAssociation association = new RelationAssociation(
                "Student_Course",
                "enrollment",
                new RelationEnd("Student", "student", Cardinality.parse("0..*")),
                new RelationEnd("Course", "course", Cardinality.parse("1..*")),
                VERSION
        );

        assertEquals(AssociationKind.RELATION, association.kind());
        assertEquals("Student", association.sourceEntityAzName());
        assertEquals("Course", association.targetEntityAzName());
        assertEquals("student", association.sourceRoleName());
        assertEquals("course", association.targetRoleName());
        assertEquals(Cardinality.parse("0..*"), association.sourceCardinality());
        assertEquals(Cardinality.parse("1..*"), association.targetCardinality());
        assertEquals(Cardinality.parse("1..*"), association.cardinality());
    }

    @Test
    void relationAssociationRejectsMatchingRoleNames() {
        assertThrows(IllegalArgumentException.class, () -> new RelationAssociation(
                "Student_Course",
                "enrollment",
                new RelationEnd("Student", "same", Cardinality.parse("0..*")),
                new RelationEnd("Course", "same", Cardinality.parse("1..*")),
                VERSION
        ));
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
