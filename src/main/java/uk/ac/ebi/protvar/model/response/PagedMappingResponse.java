package uk.ac.ebi.protvar.model.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagedMappingResponse {
	private MappingResponse content;

	private int pageNo;

	private int pageSize;

	private long totalElements;

	private int totalPages;

	private boolean last;
}
