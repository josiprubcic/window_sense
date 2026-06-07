package com.windowsense.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.windowsense", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule controllers_do_not_depend_on_persistence_or_integration =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..repository..",
                            "..entity..",
                            "..integration.."
                    );

    @ArchTest
    static final ArchRule dto_classes_do_not_depend_on_application_or_persistence_layers =
            noClasses().that().resideInAPackage("..dto..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..entity..",
                            "..service..",
                            "..repository.."
                    );

    @ArchTest
    static final ArchRule mappers_do_not_call_out_to_stateful_layers =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..repository..",
                            "..service..",
                            "..integration.."
                    );

    @ArchTest
    static final ArchRule mappers_are_stateless =
            classes().that().resideInAPackage("..mapper..")
                    .should().haveOnlyFinalFields();

    @ArchTest
    static final ArchRule repositories_are_repository_access_types =
            classes().that().resideInAPackage("..repository..")
                    .should(beRepositoryAccessTypes());

    @ArchTest
    static final ArchRule entities_do_not_depend_on_application_layers =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..dto..",
                            "..service..",
                            "..mapper..",
                            "..integration.."
                    );

    @ArchTest
    static final ArchRule legacy_domain_root_packages_are_empty =
            noClasses().should().resideInAnyPackage(
                    "com.windowsense.room..",
                    "com.windowsense.device..",
                    "com.windowsense.thingsboard..",
                    "com.windowsense.virtual..",
                    "com.windowsense.automation..",
                    "com.windowsense.model..",
                    "com.windowsense.home..",
                    "com.windowsense.user.."
            );

    private static ArchCondition<JavaClass> beRepositoryAccessTypes() {
        return new ArchCondition<>("be repository access types") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean isRepository = item.isAssignableTo(Repository.class)
                        || item.isAnnotatedWith(org.springframework.stereotype.Repository.class)
                        || item.getSimpleName().endsWith("Repository");
                events.add(new SimpleConditionEvent(
                        item,
                        isRepository,
                        item.getName() + " is not a repository access type"
                ));
            }
        };
    }
}
