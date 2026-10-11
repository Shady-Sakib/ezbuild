package ezbuild.dto;

import java.util.List;
import java.util.Map;

/** Sidebar data: brand counts, spec value counts per key, price bounds. */
public record Facets(List<FacetCount> brands, Map<String, List<FacetCount>> specs, long minPrice, long maxPrice) { }
