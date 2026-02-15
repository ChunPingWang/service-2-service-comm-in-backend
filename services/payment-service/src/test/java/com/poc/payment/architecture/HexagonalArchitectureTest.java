package com.poc.payment.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.poc.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // ── Domain layer isolation ──────────────────────────────────────────────────
    // Domain model must not depend on adapters, config, or Spring framework.

    @ArchTest
    static final ArchRule domain_model_should_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule domain_model_should_not_depend_on_config =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("..config..");

    @ArchTest
    static final ArchRule domain_model_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule domain_model_should_not_depend_on_application_service =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("..application.service..");

    @ArchTest
    static final ArchRule domain_event_should_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage("..domain.event..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule domain_event_should_not_depend_on_config =
            noClasses()
                    .that().resideInAPackage("..domain.event..")
                    .should().dependOnClassesThat().resideInAPackage("..config..");

    @ArchTest
    static final ArchRule domain_event_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain.event..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    // ── Application layer isolation ─────────────────────────────────────────────
    // Application services and ports must not depend on adapters or config.

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_config =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..config..");

    // ── Adapter layer direction ─────────────────────────────────────────────────
    // Inbound adapters should access the application layer only through inbound ports.

    @ArchTest
    static final ArchRule inbound_adapters_should_not_directly_use_application_services =
            noClasses()
                    .that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("..application.service..");

    @ArchTest
    static final ArchRule inbound_adapters_should_not_use_outbound_ports =
            noClasses()
                    .that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("..application.port.out..");

    // Outbound adapters should access the application layer only through outbound ports.

    @ArchTest
    static final ArchRule outbound_adapters_should_not_directly_use_application_services =
            noClasses()
                    .that().resideInAPackage("..adapter.out..")
                    .should().dependOnClassesThat().resideInAPackage("..application.service..");

    @ArchTest
    static final ArchRule outbound_adapters_should_not_use_inbound_ports =
            noClasses()
                    .that().resideInAPackage("..adapter.out..")
                    .should().dependOnClassesThat().resideInAPackage("..application.port.in..");

    // ── Port interface constraints ──────────────────────────────────────────────
    // Ports should be interfaces, not concrete classes.

    @ArchTest
    static final ArchRule inbound_ports_should_be_interfaces =
            classes()
                    .that().resideInAPackage("..application.port.in..")
                    .should().beInterfaces();

    @ArchTest
    static final ArchRule outbound_ports_should_be_interfaces =
            classes()
                    .that().resideInAPackage("..application.port.out..")
                    .should().beInterfaces();

    // ── No cyclic dependencies ──────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_cycles_between_packages =
            SlicesRuleDefinition.slices()
                    .matching("com.poc.payment.(*)..")
                    .should().beFreeOfCycles();
}
