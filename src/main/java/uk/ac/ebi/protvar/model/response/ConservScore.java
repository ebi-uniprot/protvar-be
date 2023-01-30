package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ConservScore {

	private String acc;
	private String aa;
	private Integer pos;
	private Double score;

}
