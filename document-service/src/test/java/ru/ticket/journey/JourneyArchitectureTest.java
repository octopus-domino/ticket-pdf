package ru.ticket.journey;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class JourneyArchitectureTest {
  @Test
  void domainIsIndependentOfOtherJourneyLayers() {
    var classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("ru.ticket.journey.domain", "ru.ticket.journey.application", "ru.ticket.journey.infrastructure");
    noClasses().that().resideInAPackage("ru.ticket.journey.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("ru.ticket.journey.infrastructure..",
            "ru.ticket.journey.application..", "ru.ticket.journey.web..")
        .check(classes);
    noClasses().that().resideInAnyPackage("ru.ticket.journey.application..", "ru.ticket.journey.infrastructure..")
        .should().dependOnClassesThat().resideInAPackage("ru.ticket.journey.web..")
        .check(classes);
  }
}
