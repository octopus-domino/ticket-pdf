package ru.ticket.journey;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class JourneyArchitectureTest {
  @Test
  void journeyClassesRespectTheirLayerBoundaries() {
    var classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("ru.ticket.journey");
    assertTrue(classes.stream().noneMatch(type -> type.getPackageName().equals("ru.ticket.journey")),
        "Journey classes must belong to a subpackage");
    noClasses().that().resideInAPackage("ru.ticket.journey.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("ru.ticket.journey.infrastructure..",
            "ru.ticket.journey.application..", "ru.ticket.journey.web..")
        .check(classes);
    noClasses().that().resideInAnyPackage("ru.ticket.journey.application..", "ru.ticket.journey.infrastructure..")
        .should().dependOnClassesThat().resideInAPackage("ru.ticket.journey.web..")
        .check(classes);
  }
}
