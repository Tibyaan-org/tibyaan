package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * One independent step of source analysis (Volume 5 section 4). Each extractor reads the parsed
 * source and contributes part of the IR; the provider assembles the parts into the whole.
 *
 * <p>The pattern lets coverage grow without reshaping the module: only {@link EntityExtractor}
 * is enabled in the MVP, and the other extractors stay as placeholders until their phase
 * (ADR-0011).
 */
public interface Extractor {

    /** Reads the given compilation units and returns this extractor's contribution to the IR. */
    ExtractorContribution extract(List<CompilationUnit> units);
}
