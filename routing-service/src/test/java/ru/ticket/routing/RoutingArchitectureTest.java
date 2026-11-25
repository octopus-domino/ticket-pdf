package ru.ticket.routing;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class RoutingArchitectureTest {
    @Test void coreAndAdaptersRemainIndependent() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ru.ticket.routing");
        noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "ru.ticket.routing.domain..").check(classes);
        noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..web..", "org.springframework..").check(classes);
        noClasses().that().resideInAPackage("..web..").should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..").check(classes);
    }

    @Test void walkingProvidersDoNotDependOnControllers() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ru.ticket.walks");
        noClasses().that().resideInAnyPackage("ru.ticket.walks.infrastructure..", "ru.ticket.walks.application..",
                        "ru.ticket.walks.domain..")
                .should().dependOnClassesThat().resideInAPackage("ru.ticket.walks.web..").check(classes);
    }
}
