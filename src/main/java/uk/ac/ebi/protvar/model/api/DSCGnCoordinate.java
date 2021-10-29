package uk.ac.ebi.protvar.model.api;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DSCGnCoordinate {
	private DSCGenomicLocation genomicLocation;
	private String ensemblGeneId;
	private String ensemblTranscriptId;
	private String ensemblTranslationId;
}
