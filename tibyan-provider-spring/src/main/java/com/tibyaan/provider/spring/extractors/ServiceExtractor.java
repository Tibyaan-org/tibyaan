package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * Placeholder (Volume 5 section 4, ADR-0011). Not enabled in the MVP.
 *
 * <p>A later phase will read service methods to enrich capabilities and replace the conventional
 * binding with the real discovered method. Until then this contributes nothing, and the provider
 * does not run it.
 */
public final class ServiceExtractor implements Extractor {

    @Override
    public ExtractorContribution extract(List<CompilationUnit> units) {
        return ExtractorContribution.empty();
    }
}
