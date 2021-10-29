package uk.ac.ebi.protvar.model;

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
