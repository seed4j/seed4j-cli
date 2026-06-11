package com.seed4j.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

@UnitTest
class HexagonalArchTest {

  private static final String ROOT_PACKAGE = "com.seed4j.cli";
  private static final String COMPOSITION_PACKAGES = ROOT_PACKAGE.concat(".bootstrap.composition..");

  private static final JavaClasses classes = new ClassFileImporter()
    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
    .importPackages(ROOT_PACKAGE);

  private static final Collection<String> businessContexts = packagesWithAnnotation(BusinessContext.class);
  private static final Collection<String> businessContextsPackages = buildPackagesPatterns(businessContexts);

  private static final Collection<String> sharedKernels = packagesWithAnnotation(SharedKernel.class);
  private static final Collection<String> sharedKernelsPackages = buildPackagesPatterns(sharedKernels);

  // The empty package is related to https://github.com/TNG/ArchUnit/issues/191#issuecomment-507964792
  private static final Collection<String> vanillaPackages = List.of(
    "",
    "java.lang..",
    "java.math..",
    "java.nio.file..",
    "java.time..",
    "java.util.."
  );
  private static final Collection<String> commonToolsAndUtilsPackages = List.of(
    "org.apache.commons..",
    "org.jspecify.annotations..",
    "com.google.guava.."
  );
  private static final Collection<String> forbiddenDomainTechnicalPackages = List.of(
    "java.io..",
    "java.net..",
    "java.security..",
    "java.util.jar..",
    "java.util.zip..",
    "org.slf4j..",
    "ch.qos.logback..",
    "org.yaml.snakeyaml..",
    "org.springframework.."
  );
  private static final Collection<Class<?>> forbiddenDomainTechnicalClasses = List.of(
    Files.class,
    java.nio.file.StandardCopyOption.class,
    java.nio.file.FileAlreadyExistsException.class
  );

  private static Collection<String> buildPackagesPatterns(Collection<String> packages) {
    return packages
      .stream()
      .map(path -> path + "..")
      .toList();
  }

  private static Collection<String> packagesWithAnnotation(Class<? extends Annotation> annotationClass) throws AssertionError {
    try (Stream<Path> files = Files.walk(rootPackagePath())) {
      return files
        .filter(path -> path.toString().endsWith("package-info.java"))
        .map(toPackageInfoName())
        .map(path -> path.replaceAll("[/]", "."))
        .map(path -> path.replaceAll("[\\\\]", "."))
        .map(path -> path.replace("src.main.java.", ""))
        .map(toPackage())
        .filter(pack -> pack.getAnnotation(annotationClass) != null)
        .map(Package::getName)
        .toList();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static Path rootPackagePath() {
    return Stream.of(ROOT_PACKAGE.split("\\."))
      .map(Path::of)
      .reduce(Path.of("src", "main", "java"), Path::resolve);
  }

  private static Function<Path, String> toPackageInfoName() {
    return path -> {
      String stringPath = path.toString();
      return stringPath.substring(0, stringPath.lastIndexOf("."));
    };
  }

  private static Function<String, Package> toPackage() {
    return path -> {
      try {
        return Class.forName(path).getPackage();
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }
    };
  }

  @Nested
  class BoundedContexts {

    @Test
    void shouldNotDependOnOtherBoundedContextDomains() {
      Stream.concat(businessContexts.stream(), sharedKernels.stream()).forEach(context ->
        noClasses()
          .that()
          .resideInAnyPackage(context + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(otherBusinessContextsDomains(context))
          .because("contexts can only depend on domain classes in the same context or shared kernels")
          .check(classes)
      );
    }

    @Test
    void shouldNotDependOnOtherBoundedContextApplications() {
      Stream.concat(businessContexts.stream(), sharedKernels.stream()).forEach(context ->
        noClasses()
          .that()
          .resideInAnyPackage(context + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(otherBusinessContextsApplications(context))
          .because("bounded contexts must communicate through adapters instead of another context's application layer")
          .check(classes)
      );
    }

    private String[] otherBusinessContextsDomains(String context) {
      return businessContexts
        .stream()
        .filter(other -> !context.equals(other))
        .map(name -> name + ".domain..")
        .toArray(String[]::new);
    }

    private String[] otherBusinessContextsApplications(String context) {
      return businessContexts
        .stream()
        .filter(other -> !context.equals(other))
        .map(name -> name + ".application..")
        .toArray(String[]::new);
    }
  }

  @Nested
  class Domain {

    @Test
    void portsShouldBeNamedByBusinessCapability() {
      classes()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .areInterfaces()
        .should(notHaveSimpleNameEndingWith("Port"))
        .because("domain ports should be named by the business capability")
        .check(classes);
    }

    @Test
    void shouldNotDependOnOutside() {
      classes()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(authorizedDomainPackages())
        .because("domain model should only depend on domains and a very limited set of external dependencies")
        .check(classes);
    }

    @Test
    void shouldNotDependOnTechnicalPackages() {
      noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(forbiddenDomainTechnicalPackages.toArray(String[]::new))
        .because("domain code must not perform technical I/O, logging, jar, yaml, network, security, or Spring work")
        .check(classes);
    }

    @Test
    void shouldNotDependOnTechnicalFileSystemClasses() {
      forbiddenDomainTechnicalClasses.forEach(forbiddenClass ->
        noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(forbiddenClass)
          .because("domain code may model Path values but must not perform filesystem operations")
          .check(classes)
      );
    }

    @Test
    void domainAggregatesShouldUseValueObjectsForBusinessConcepts() {
      classes()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .areNotInterfaces()
        .should(notExposeMultipleRawConceptValues())
        .because("domain aggregates should expose Value Objects instead of multiple raw business values")
        .check(classes);
    }

    private String[] authorizedDomainPackages() {
      return Stream.of(List.of("..domain.."), vanillaPackages, commonToolsAndUtilsPackages, sharedKernelsPackages)
        .flatMap(Collection::stream)
        .toArray(String[]::new);
    }
  }

  @Nested
  class Application {

    @Test
    void shouldNotDependOnInfrastructure() {
      noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infrastructure..")
        .because("application should orchestrate domain use cases without depending on infrastructure adapters")
        .check(classes);
    }
  }

  @Nested
  class Primary {

    @Test
    void shouldNotDependOnSecondary() {
      noClasses()
        .that()
        .resideInAPackage("..primary..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..secondary..")
        .because("primary adapters should not interact directly with secondary adapters")
        .check(classes);
    }

    @Test
    void javaSpringPrimaryAdaptersShouldOnlyBeAccessedBySecondaryAdapters() {
      classes()
        .that()
        .resideInAPackage("..infrastructure.primary..")
        .and()
        .haveSimpleNameStartingWith("Java")
        .and()
        .areAnnotatedWith(Component.class)
        .should()
        .onlyHaveDependentClassesThat()
        .resideInAPackage("..infrastructure.secondary..")
        .because("Java primary adapters are cross-context entry points and should be called from secondary adapters")
        .check(classes);
    }
  }

  @Nested
  class Secondary {

    @Test
    void domainPortImplementationsShouldBeNamedByMechanismAndCapability() {
      classes()
        .that()
        .resideInAPackage("..infrastructure.secondary..")
        .should(notEndWithAdapterWhenImplementingDomainPort())
        .because("secondary implementations of domain ports should use mechanism plus business capability names")
        .check(classes);
    }

    @Test
    void secondaryImplementedDomainPortsShouldBeUsedByDomainOrApplication() {
      classes()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .areInterfaces()
        .should(beUsedByDomainOrApplicationWhenImplementedBySecondary())
        .because("domain ports should model capabilities required by domain or application behavior, not adapter-internal seams")
        .check(classes);
    }

    @Test
    void shouldNotDependOnApplication() {
      noClasses()
        .that()
        .resideInAPackage("..infrastructure.secondary..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..application..")
        .because("secondary adapters should implement domain ports without depending on application services")
        .check(classes);
    }

    @Test
    void shouldNotDependOnSameContextPrimary() {
      Stream.concat(businessContexts.stream(), sharedKernels.stream()).forEach(context ->
        noClasses()
          .that()
          .resideInAPackage(context + ".infrastructure.secondary..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(context + ".infrastructure.primary..")
          .because("secondary adapters should not loop to their own context primary adapters")
          .check(classes)
      );
    }
  }

  @Nested
  class SharedKernels {

    @Test
    void sharedPackageShouldOnlyContainSharedKernels() {
      classes()
        .that()
        .haveSimpleName("package-info")
        .and()
        .resideInAPackage(ROOT_PACKAGE.concat(".shared.."))
        .and()
        .resideOutsideOfPackages(ROOT_PACKAGE.concat(".shared..domain"), ROOT_PACKAGE.concat(".shared..infrastructure.*"))
        .should()
        .beMetaAnnotatedWith(SharedKernel.class)
        .because(ROOT_PACKAGE + ".shared package should only contain shared kernels")
        .check(classes);
    }
  }

  @Nested
  class CompositionRoot {

    @Test
    void shouldOnlyBeAccessedByRootApplicationEntryPoint() {
      noClasses()
        .that()
        .resideOutsideOfPackage(COMPOSITION_PACKAGES)
        .and()
        .doNotHaveFullyQualifiedName(ROOT_PACKAGE.concat(".Seed4JCliApp"))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(COMPOSITION_PACKAGES)
        .because("pre-Spring composition is only allowed from the root application entry point")
        .check(classes);
    }

    @Test
    void boundedContextsAndSharedKernelsShouldNotDependOnComposition() {
      noClasses()
        .that()
        .resideInAnyPackage(businessContextsOrSharedKernelsPackages())
        .and()
        .resideOutsideOfPackage(COMPOSITION_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(COMPOSITION_PACKAGES)
        .because("pre-Spring composition must stay an explicit composition root, not a shared dependency")
        .check(classes);
    }

    private String[] businessContextsOrSharedKernelsPackages() {
      return Stream.of(businessContextsPackages, sharedKernelsPackages)
        .flatMap(Collection::stream)
        .toArray(String[]::new);
    }
  }

  private static ArchCondition<JavaClass> notHaveSimpleNameEndingWith(String suffix) {
    return new ArchCondition<>("not have simple name ending with " + suffix) {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.getSimpleName().endsWith(suffix)) {
          events.add(SimpleConditionEvent.violated(item, item.getName() + " ends with " + suffix));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notEndWithAdapterWhenImplementingDomainPort() {
    return new ArchCondition<>("not end with Adapter when implementing a domain port") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.getSimpleName().endsWith("Adapter") && implementsDomainPort(item)) {
          events.add(SimpleConditionEvent.violated(item, item.getName() + " implements a domain port and ends with Adapter"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> beUsedByDomainOrApplicationWhenImplementedBySecondary() {
    return new ArchCondition<>("be used by domain or application when implemented by secondary infrastructure") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (implementedBySecondary(item) && unusedByDomainOrApplication(item)) {
          events.add(
            SimpleConditionEvent.violated(
              item,
              item.getName() + " is implemented by secondary infrastructure but is not used by domain or application"
            )
          );
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notExposeMultipleRawConceptValues() {
    return new ArchCondition<>("not expose multiple raw concept values") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (notRecord(item)) {
          return;
        }

        List<String> rawConceptComponents = rawConceptComponentNames(item);
        if (rawConceptComponents.size() > 1) {
          events.add(
            SimpleConditionEvent.violated(
              item,
              item.getName() + " exposes multiple raw concept values: " + String.join(", ", rawConceptComponents)
            )
          );
        }
      }
    };
  }

  private static boolean notRecord(JavaClass item) {
    return !item.reflect().isRecord();
  }

  private static List<String> rawConceptComponentNames(JavaClass item) {
    return Stream.of(item.reflect().getRecordComponents())
      .filter(HexagonalArchTest::rawConceptComponent)
      .map(RecordComponent::getName)
      .toList();
  }

  private static boolean rawConceptComponent(RecordComponent component) {
    Class<?> type = component.getType();

    return (
      type.isPrimitive()
      || type.equals(String.class)
      || type.equals(String[].class)
      || type.equals(Path.class)
      || optionalRawConcept(component.getGenericType())
    );
  }

  private static boolean optionalRawConcept(Type type) {
    String typeName = type.getTypeName();

    return (
      typeName.equals("java.util.Optional<java.lang.String>")
      || typeName.equals("java.util.Optional<java.nio.file.Path>")
      || typeName.equals("java.util.Optional<java.lang.Boolean>")
    );
  }

  private static boolean implementedBySecondary(JavaClass item) {
    return classes
      .stream()
      .filter(HexagonalArchTest::secondaryInfrastructureClass)
      .anyMatch(secondaryClass -> secondaryClass.getAllRawInterfaces().contains(item));
  }

  private static boolean secondaryInfrastructureClass(JavaClass item) {
    return item.getPackageName().contains(".infrastructure.secondary");
  }

  private static boolean unusedByDomainOrApplication(JavaClass item) {
    return item
      .getDirectDependenciesToSelf()
      .stream()
      .map(Dependency::getOriginClass)
      .noneMatch(HexagonalArchTest::domainOrApplicationClass);
  }

  private static boolean domainOrApplicationClass(JavaClass item) {
    return domainClass(item) || item.getPackageName().contains(".application");
  }

  private static boolean implementsDomainPort(JavaClass item) {
    return item.getAllRawInterfaces().stream().anyMatch(HexagonalArchTest::domainClass);
  }

  private static boolean domainClass(JavaClass item) {
    return item.getPackageName().startsWith(ROOT_PACKAGE) && item.getPackageName().contains(".domain");
  }
}
