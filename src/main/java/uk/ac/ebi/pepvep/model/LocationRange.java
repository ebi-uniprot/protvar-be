package uk.ac.ebi.pepvep.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class LocationRange {
	private Long start;
	private Long end;
}
