package uk.ac.ebi.protvar.model.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProteinFeature {
	private int begin;
	private int end;
	private String description;
	private String type;
	private String category;
	private String typeDescription;
	private List<Evidence> evidences;

	public void setBegin(String begin) {
		try {
			this.begin = Integer.parseInt(begin);
		} catch (NumberFormatException ex) {
			this.begin = Integer.MAX_VALUE;
		}
	}

	public void setEnd(String end) {
		try {
			this.end = Integer.parseInt(end);
		} catch (NumberFormatException ex) {
			this.end = Integer.MIN_VALUE;
		}
	}

}
