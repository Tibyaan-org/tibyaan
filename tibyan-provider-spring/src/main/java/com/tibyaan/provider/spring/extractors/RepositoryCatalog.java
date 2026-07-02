package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An index of the scanned source that the DTO-aware read discovery needs (Volume 5 section 4.2,
 * condition 4). It records Spring Data repositories by the framework contract (an interface
 * extending a recognized supertype) and gives access to every declared class or interface so the
 * matcher can resolve hand-written repository interfaces, their {@code @Repository} implementations,
 * and mapper declarations. It reads only what the framework itself defines; application naming
 * carries no weight here.
 *
 * <p>The RepositoryExtractor is enabled only to the extent condition 4 requires: recognizing Spring
 * Data repositories of an entity and the one-hop hand-written delegation. It builds no
 * repository-based discovery beyond that.
 */
public final class RepositoryCatalog {

    // The Spring Data repository supertypes whose second type argument is the id and first is the
    // entity. These are framework contracts, the only names with weight (section 4.2.7).
    private static final Set<String> SPRING_DATA_SUPERTYPES = Set.of(
            "JpaRepository",
            "CrudRepository",
            "ListCrudRepository",
            "PagingAndSortingRepository",
            "ListPagingAndSortingRepository");

    private static final String REPOSITORY_ANNOTATION = "Repository";

    private final Map<String, ClassOrInterfaceDeclaration> bySimpleName;
    private final Map<String, SpringDataRepo> springDataByName;

    private RepositoryCatalog(Map<String, ClassOrInterfaceDeclaration> bySimpleName,
                              Map<String, SpringDataRepo> springDataByName) {
        this.bySimpleName = bySimpleName;
        this.springDataByName = springDataByName;
    }

    /** Builds the catalog from every class and interface declared across the parsed units. */
    public static RepositoryCatalog from(List<CompilationUnit> units) {
        Map<String, ClassOrInterfaceDeclaration> bySimpleName = new HashMap<>();
        Map<String, SpringDataRepo> springData = new HashMap<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                bySimpleName.putIfAbsent(decl.getNameAsString(), decl);
                springDataRepoOf(decl).ifPresent(repo -> springData.putIfAbsent(repo.interfaceSimpleName(), repo));
            }
        }
        return new RepositoryCatalog(bySimpleName, springData);
    }

    /** The Spring Data repository declaration for this interface simple name, if it is one. */
    public Optional<SpringDataRepo> springDataRepo(String simpleName) {
        return Optional.ofNullable(springDataByName.get(simpleName));
    }

    /** The class or interface declared under this simple name, if any is in the scanned tree. */
    public Optional<ClassOrInterfaceDeclaration> declaration(String simpleName) {
        return Optional.ofNullable(bySimpleName.get(simpleName));
    }

    /** The {@code @Repository} classes implementing the interface with this simple name. */
    public List<ClassOrInterfaceDeclaration> repositoryImplementationsOf(String interfaceSimpleName) {
        List<ClassOrInterfaceDeclaration> impls = new ArrayList<>();
        for (ClassOrInterfaceDeclaration decl : bySimpleName.values()) {
            if (decl.isInterface() || !hasAnnotation(decl, REPOSITORY_ANNOTATION)) {
                continue;
            }
            if (implementsInterface(decl, interfaceSimpleName)) {
                impls.add(decl);
            }
        }
        return impls;
    }

    /**
     * Whether a field of this declared type is repository-typed for condition 6: a Spring Data
     * repository, or a hand-written interface that has a {@code @Repository} implementation in-tree.
     */
    public boolean isRepositoryType(String simpleName) {
        if (springDataByName.containsKey(simpleName)) {
            return true;
        }
        Optional<ClassOrInterfaceDeclaration> decl = declaration(simpleName);
        return decl.isPresent() && decl.get().isInterface()
                && !repositoryImplementationsOf(simpleName).isEmpty();
    }

    private static Optional<SpringDataRepo> springDataRepoOf(ClassOrInterfaceDeclaration decl) {
        if (!decl.isInterface()) {
            return Optional.empty();
        }
        for (ClassOrInterfaceType extended : decl.getExtendedTypes()) {
            if (!SPRING_DATA_SUPERTYPES.contains(extended.getNameAsString())) {
                continue;
            }
            Optional<com.github.javaparser.ast.NodeList<com.github.javaparser.ast.type.Type>> args =
                    extended.getTypeArguments();
            if (args.isEmpty() || args.get().size() != 2) {
                continue;
            }
            String entity = lastSegment(args.get().get(0).asString());
            String idType = args.get().get(1).asString();
            return Optional.of(new SpringDataRepo(decl.getNameAsString(), entity, idType));
        }
        return Optional.empty();
    }

    private static boolean implementsInterface(ClassOrInterfaceDeclaration impl, String interfaceSimpleName) {
        return impl.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(interfaceSimpleName));
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> simpleName.equals(lastSegment(annotation.getNameAsString())));
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
