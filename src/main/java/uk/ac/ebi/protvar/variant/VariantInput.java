package uk.ac.ebi.protvar.variant;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@ToString
@Builder
@Accessors(fluent = true)
public class VariantInput {
    private int index; // order of the input
    @NonNull
    private String raw; // always contains the original input string (also used for ID type variants)
    @NonNull
    private VariantFormat format;
    @NonNull
    private VariantType type;

    // Parsed fields — optional, nullable if not applicable
    private String chromosome; // genomic only, nullable otherwise
    private Integer position; // genomic or protein position
    private String ref; // ref base (genomic) or ref AA (protein)
    private String alt; // alt base (genomic) or alt AA (protein)
    private String accession; // protein only, nullable otherwise
    private boolean valid;
    @Singular
    private List<String> errors = new ArrayList<>();

    // Genomic variants resolved from this input
    private List<GenomicVariant> genomicVariants;

    public static VariantInput parse(int index, String input) {
        if (input == null || input.isBlank()) {
            return invalid(input, "Empty input");
        }

        String trimmed = input.trim();
        VariantFormat format = VariantFormat.fromString(trimmed);
        VariantType type = format.getType();

        VariantInputBuilder builder = VariantInput.builder()
                .index(index)
                .raw(trimmed)
                .format(format)
                .type(type);

        try {
            switch (format) {
                case VCF, CUSTOM_GENOMIC -> parseCustomGenomic(trimmed, builder);
                case HGVS_GENOMIC, HGVS_CODING, HGVS_PROTEIN -> parseHGVS(trimmed, builder);
                case CUSTOM_PROTEIN -> parseCustomProtein(trimmed, builder);
                case GNOMAD -> parseGnomad(trimmed, builder);
                case DBSNP, CLINVAR, COSMIC -> {
                    // raw contains the ID, no extra parsing
                }
                default -> {
                    return invalid(trimmed, "Unrecognized format");
                }
            }
            return builder.valid(true).build();
        } catch (Exception e) {
            return invalid(trimmed, e.getMessage());
        }
    }

    public static VariantInput parse(String raw) {
        return parse(raw, null);
    }

    public static VariantInput parse(String raw, String formatHint) {
        VariantInputBuilder builder = VariantInput.builder();
        builder.raw(raw);

        try {
            VariantFormat hint = formatHint != null ? VariantFormat.fromString(formatHint) : null;

            // Your logic to resolve actual format
            VariantFormat detected = VariantFormat.detectFormat(raw);
            builder.format(detected);
            builder.type(detected.getType());

            if (hint != null && !hint.equals(detected)) {
                builder.error("Format mismatch: expected " + hint + " but detected " + detected);
            }

            builder.valid(true);
        } catch (Exception ex) {
            builder.valid(false);
            builder.error("Parsing failed: " + ex.getMessage());
        }

        return builder.build();
    }

    private static void parseCustomGenomic(String input, VariantInputBuilder builder) {
        String[] parts = input.split("[-:]");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid custom genomic format");
        builder.chromosome(parts[0]);
        builder.position(Integer.parseInt(parts[1]));
        if (parts.length > 2) builder.ref(parts[2]);
        if (parts.length > 3) builder.alt(parts[3]);
    }

    private static void parseCustomProtein(String input, VariantInputBuilder builder) {
        String[] parts = input.split("-");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid custom protein format");
        builder.accession(parts[0]);
        builder.position(Integer.parseInt(parts[1]));
        if (parts.length > 2) builder.ref(parts[2]);
        if (parts.length > 3) builder.alt(parts[3]);
    }

    private static void parseHGVS(String input, VariantInputBuilder builder) {
        // TODO: Implement proper HGVS parsing
    }

    private static void parseGnomad(String input, VariantInputBuilder builder) {
        parseCustomGenomic(input, builder);
    }

    private static VariantInput invalid(String input, String message) {
        return VariantInput.builder()
                .raw(input)
                .format(VariantFormat.INVALID)
                .type(VariantType.INVALID)
                .valid(false)
                .error(message)
                .build();
    }

    // Optional getters for parsed fields
    public Optional<String> getChromosome() { return Optional.ofNullable(chromosome); }
    public Optional<Integer> getPosition() { return Optional.ofNullable(position); }
    public Optional<String> getRef() { return Optional.ofNullable(ref); }
    public Optional<String> getAlt() { return Optional.ofNullable(alt); }
    public Optional<String> getAccession() { return Optional.ofNullable(accession); }
}
