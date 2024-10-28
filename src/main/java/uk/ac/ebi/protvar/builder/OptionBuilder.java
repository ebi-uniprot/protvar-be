package uk.ac.ebi.protvar.builder;

import java.util.ArrayList;
import java.util.List;

public class OptionBuilder {

	public enum OPTIONS {
		FUNCTION("ReferenceFunction", "protein features"),
		POPULATION("PopulationObservation", "Associated Disease and Frequencies"),
		STRUCTURE("ProteinStructure", "pdbe structure");

		OPTIONS(String optionName, String optionDescription) {
		}
	}

	public static List<OPTIONS> build(boolean function, boolean population, boolean structure) {
		List<OPTIONS> options = new ArrayList<>();
		if (function)
			options.add(OPTIONS.FUNCTION);
		if (population)
			options.add(OPTIONS.POPULATION);
		if (structure)
			options.add(OPTIONS.STRUCTURE);

		return options;
	}

}
