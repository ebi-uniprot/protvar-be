package uk.ac.ebi.protvar.model.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagedMappingResponse {
	private MappingResponse content;

	private String id;

	private int page;

	private int pageSize;

	private String assembly;

	private long totalItems;

	private int totalPages;

	private boolean last;
	private long ttl;
}
