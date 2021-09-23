package uk.ac.ebi.pepvep.model.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Setter
public class GenomeLocation {
	private Position begin;
	private Position end;
	private Position position;
}

