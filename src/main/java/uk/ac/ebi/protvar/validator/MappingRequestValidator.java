package uk.ac.ebi.protvar.validator;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.SearchTerm;
import uk.ac.ebi.protvar.types.SearchType;
import uk.ac.ebi.protvar.utils.SearchTypeResolver;

import java.util.List;

/**
 * Validator for MappingRequest and its subclasses (e.g., DownloadRequest).
 * Provides common validation logic for search terms and filters.
 */
@Component
public class MappingRequestValidator {

    /**
     * Validates and processes a mapping request.
     * - Validates request structure
     * - Resolves search term types (if not specified)
     * - Normalizes values (uppercase, except for PDB and INPUT_ID)
     *
     * @param request The request to validate and process
     * @return ValidationResult indicating success or failure with error message
     */
    public ValidationResult validateAndProcess(MappingRequest request) {
        // First validate the structure
        ValidationResult structureValidation = validateStructure(request);
        if (!structureValidation.isValid()) {
            return structureValidation;
        }

        // Then process search terms (resolve types and normalize)
        return processSearchTerms(request);
    }

    /**
     * Validates the request structure based on search term types and filters.
     */
    public ValidationResult validateStructure(MappingRequest request) {
        List<SearchTerm> terms = request.getSearchTerms();

        // Case 1: No search terms - must have at least one filter
        if (request.hasNoSearchTerms()) {
            if (!request.hasAnyFilter()) {
                return ValidationResult.error(
                        "When no search terms are provided, at least one filter must be specified"
                );
            }
            return ValidationResult.valid();
        }

        // Case 2: Single variant - must be alone, no filters typically needed
        if (terms.stream().anyMatch(t -> t.getType() == SearchType.VARIANT)) {
            if (terms.size() > 1) {
                return ValidationResult.error(
                        "VARIANT search term cannot be combined with other search terms"
                );
            }
            return ValidationResult.valid();
        }

        // Case 3: Input ID - must be alone
        if (terms.stream().anyMatch(t -> t.getType() == SearchType.INPUT_ID)) {
            if (terms.size() > 1) {
                return ValidationResult.error(
                        "INPUT_ID search term cannot be combined with other search terms"
                );
            }
            return ValidationResult.valid();
        }

        // Case 4: Identifiers only - all must be identifier types
        boolean allIdentifiers = terms.stream()
                .allMatch(t -> t.getType() != null && t.getType().isIdentifier());

        if (!allIdentifiers) {
            return ValidationResult.error(
                    "Cannot mix VARIANT or INPUT_ID with identifier search terms"
            );
        }

        // If no identifiers, must have filters (already checked above, but being explicit)
        if (terms.isEmpty() && !request.hasAnyFilter()) {
            return ValidationResult.error(
                    "Advanced search requires at least one identifier or filter"
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Process search terms: resolve types and normalize values.
     * Modifies the request in place.
     */
    private ValidationResult processSearchTerms(MappingRequest request) {
        if (request.getSearchTerms() == null) {
            return ValidationResult.valid();
        }

        for (SearchTerm term : request.getSearchTerms()) {
            // Auto-resolve type if not specified
            if (term.getType() == null) {
                SearchType resolved = SearchTypeResolver.resolve(term.getValue());
                if (resolved == null) {
                    return ValidationResult.error(
                            "Unable to resolve type for search term: " + term.getValue() +
                                    ". Please specify the type explicitly or check the format."
                    );
                }
                term.setType(resolved);
            }

            // Normalize case (exclude PDB and INPUT_ID which are case-sensitive)
            if (term.getType() != SearchType.PDB &&
                    term.getType() != SearchType.INPUT_ID) {
                term.setValue(term.getValue().toUpperCase());
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Result of validation containing success status and optional error message.
     */
    @Data
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}