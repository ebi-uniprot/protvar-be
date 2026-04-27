package uk.ac.ebi.protvar.model.response;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PagedMappingResponse {
	private MappingResponse content;

	private int page;

	private int pageSize;

	private String assembly;

	private long totalItems;

	private int totalPages;

	/**
	 * Upper bound on totalItems. Set on filter-only browse where the
	 * underlying COUNT(*) is capped to bound query cost; null on paths that
	 * return an exact total (identifier / variant / uploaded result).
	 *
	 * Convention: if totalItems > totalCap, the actual count is "more than
	 * totalCap" — clients should display it accordingly (e.g. "10,000+").
	 */
	private Long totalCap;

	private boolean last;
	//private long ttl;
}
