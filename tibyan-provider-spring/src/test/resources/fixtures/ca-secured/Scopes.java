package ca.secured;

// The authority-constant holder: public static final String literals (the benchmark idiom). BAD_CONST
// has a concatenated initializer, not a single literal, so it does not resolve (CA-12b).
class Scopes {
    public static final String PREFIX = "48";
    public static final String WAREHOUSE_READ = "480012";
    public static final String STOCK_READ = "480013";
    public static final String BAD_CONST = PREFIX + "0014";
}
