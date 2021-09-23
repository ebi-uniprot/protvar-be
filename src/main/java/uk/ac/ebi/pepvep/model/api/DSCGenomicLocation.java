package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Setter
public class DSCGenomicLocation {
	private String chromosome;
	private long start;
	private long end;
	private boolean reverseStrand;
	private List<Exon> exon;
	
}

