package com.tibyaan.core.ir;

/**
 * The tool's own read-safety label on a reviewed capability (Volume 4 section 3.10; Volume 5 section 4.17).
 * It is what the tool could establish about the method, never a proof of its meaning: READ means no
 * mutation was visible in the analyzed slice, WRITE means one was, UNKNOWN means the check could not decide
 * or the method returns nothing. VERIFIED and ATTESTED are not labels here: they are the other two tiers.
 */
public enum ReviewLabel {
    READ,
    WRITE,
    UNKNOWN;

    /** The capability kind a label maps to at intake (Volume 5 section 4.17.3, RV-H). */
    public CapabilityKind kind() {
        return switch (this) {
            case READ -> CapabilityKind.READ;
            case WRITE -> CapabilityKind.WRITE;
            case UNKNOWN -> CapabilityKind.ACTION;
        };
    }
}
