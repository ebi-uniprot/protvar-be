package uk.ac.ebi.protvar.model.response;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PagedMappingResponse { // todo: consider using Page<MappingResponse> and moving
	// non-page fields (e.g. id, assembly) to MappingResponse, or better have the response contain
	// the original mapping request object
	private MappingResponse content;

	private String id; // is the input e.g. inputId, proteinAcc, etc.

	private int page;

	private int pageSize;

	private String assembly;

	private long totalItems;

	private int totalPages;

	private boolean last;
	//private long ttl;
}
