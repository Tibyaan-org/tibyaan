package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * Placeholder (Volume 5 section 4, ADR-0011). Not enabled in the MVP.
 *
 * <p>A later phase will read controllers to learn the operations the system already exposes.
 * Until then this contributes nothing, and the provider does not run it.
 */
public final class ControllerExtractor implements Extractor {

    @Override
    public ExtractorContribution extract(List<CompilationUnit> units) {
        return ExtractorContribution.empty();
    }
}
