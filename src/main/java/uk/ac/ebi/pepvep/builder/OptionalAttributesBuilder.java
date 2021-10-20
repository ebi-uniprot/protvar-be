package uk.ac.ebi.pepvep.builder;

import java.util.List;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.fetcher.PDBeFetcher;
import uk.ac.ebi.pepvep.fetcher.ProteinFetcher;
import uk.ac.ebi.pepvep.fetcher.VariationFetcher;
import uk.ac.ebi.pepvep.utils.Constants;
import uk.ac.ebi.pepvep.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.pepvep.model.response.IsoFormMapping;
import uk.ac.ebi.pepvep.model.response.PDBeStructure;
import uk.ac.ebi.pepvep.model.response.PopulationObservation;
import uk.ac.ebi.pepvep.model.response.Protein;
import uk.ac.ebi.pepvep.model.response.Variation;

@Service
@AllArgsConstructor
public class OptionalAttributesBuilder {

	private static final String UNIPROT_API_PEPVEP_BASE = "/";
	private static final String STRUCTURE_API = "structure";
	private static final String FUNCTION_API = "function";
	private static final String POPULATION_API = "population";

	private VariationFetcher variationFetcher;
	private ProteinFetcher proteinFetcher;
	private PDBeFetcher pdbeFetcher;

	public void build(String accession, long genomicLocation, int isoformPostion, List<OPTIONS> options,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		buildPopulationObservation(accession, isoformPostion, options.contains(OPTIONS.POPULATION), genomicLocation, builder);

		buildFunction(accession, isoformPostion, options.contains(OPTIONS.FUNCTION), builder);

		buildStructure(accession, isoformPostion, options.contains(OPTIONS.STRUCTURE), builder);
	}

	private void buildStructure(String accession, int isoformPostion, boolean isStructure,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isStructure) {
			List<PDBeStructure> proteinStructure = pdbeFetcher.fetchByAccession(accession, isoformPostion);
			builder.proteinStructure(proteinStructure);
		} else {
			String uri = buildUri(STRUCTURE_API, accession, isoformPostion);
			builder.proteinStructureUri(uri);
		}
	}

	private void buildFunction(String accession, int isoformPostion, boolean isFunction,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isFunction) {
			Protein protein = proteinFetcher.fetch(accession, isoformPostion);
			builder.referenceFunction(protein);
		} else {
			String uri = buildUri(FUNCTION_API, accession, isoformPostion);
			builder.referenceFunctionUri(uri);
		}
	}

	private void buildPopulationObservation(String accession, int isoformPostion, boolean isVariation, long genomicLocation,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isVariation) {
			String proteinLocation = isoformPostion + Constants.SPLITTER + isoformPostion;
			List<Variation> variations = variationFetcher.fetchByAccession(accession, proteinLocation);
			PopulationObservation populationObservation = new PopulationObservation();
			populationObservation.setProteinColocatedVariant(variations);
			builder.populationObservations(populationObservation);
		} else {
			String uri = buildUri(accession, isoformPostion, genomicLocation);
			builder.populationObservationsUri(uri);
		}
	}

	private static String buildUri(String operation, String accession, int position) {
		return UNIPROT_API_PEPVEP_BASE + operation + Constants.SLASH + accession + Constants.SLASH
			+ position;

	}

	private static String buildUri(String accession, int isoformPostion, long genomicLocation) {
		return UNIPROT_API_PEPVEP_BASE + POPULATION_API + Constants.SLASH + accession + Constants.SLASH
			+ isoformPostion + "?genomicLocation=" + genomicLocation;
	}
}
