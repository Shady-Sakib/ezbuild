package ezbuild.dto;

import java.util.List;

/** Compatibility result. ok = no errors; warnings are allowed. */
public record CompatReport(boolean ok, List<String> errors, List<String> warnings, int estimatedWattage) {

    public CompatReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public String badge() {
        if (!ok) return "Incompatible";
        return warnings.isEmpty() ? "Compatible" : "Compatible with notes";
    }
}
