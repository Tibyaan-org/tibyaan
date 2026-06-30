package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * One independent step of source analysis (Volume 5 section 4). An extractor reads the parsed
 * source and contributes a self-contained slice of the IR; the provider concatenates the slices.
 *
 * <p>This is the contract for the still-placeholder repository, controller, and annotation
 * extractors, which will each contribute independently. The Entity and Service extractors do not
 * implement it: they cooperate to discover a binding, a join the provider owns, so they have
 * purpose-specific shapes ({@link EntityExtractor#extract} and {@link ServiceExtractor#discover})
 * rather than this uniform contribution shape (Volume 5 section 4.1).
 */
public interface Extractor {

    /** Reads the given compilation units and returns this extractor's contribution to the IR. */
    ExtractorContribution extract(List<CompilationUnit> units);
}
