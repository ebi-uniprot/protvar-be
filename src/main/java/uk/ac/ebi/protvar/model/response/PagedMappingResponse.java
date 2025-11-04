package uk.ac.ebi.protvar.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import uk.ac.ebi.protvar.model.MappingRequest;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Paged response containing variant mappings and metadata")
public class PagedMappingResponse { // todo: consider using Page<MappingResponse>
    @Schema(description = "The original request showing search terms, filters, and other parameters used for this query")
    private MappingRequest request;

    @Schema(description = "The variant mapping data for this page")
	private MappingResponse mapping;

    // Pagination metadata (actual results may differ from request in edge cases)
    @Schema(description = "Actual page number returned (1-based). May differ from requested page if out of range.", example = "1")
	private int page;

    @Schema(description = "Actual number of items per page. May differ from requested pageSize if adjusted.", example = "50")
	private int pageSize;

    @Schema(description = "Total number of items across all pages", example = "1523")
    private long totalItems;

    @Schema(description = "Total number of pages", example = "31")
	private int totalPages;

    @Schema(description = "Whether this is the last page", example = "false")
	private boolean last;
	//private long ttl;
}
