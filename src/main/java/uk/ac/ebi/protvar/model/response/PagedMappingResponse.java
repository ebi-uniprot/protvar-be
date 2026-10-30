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

	/**
	 * Set while an upload is being prepared for filter/sort: build progress 0–99. The client polls
	 * the same request until it is null (content is empty meanwhile).
	 */
	private Integer preparing;

	/**
	 * Uploaded result only: how many inputs the upload has in total, whatever the filters. With filters on,
	 * totalItems counts the matching inputs, so the client can show "N of totalInputs".
	 */
	private Long totalInputs;

	/** Uploaded result only, once the upload is prepared: mapped/not mapped lines and notes by code. */
	private UploadSummary uploadSummary;
	//private long ttl;
}
