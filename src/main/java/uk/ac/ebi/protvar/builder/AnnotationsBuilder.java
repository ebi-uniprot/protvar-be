package uk.ac.ebi.protvar.builder;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.fetcher.PDBeFetcher;
import uk.ac.ebi.protvar.fetcher.ProteinsFetcher;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.model.response.IsoFormMapping;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Service
@AllArgsConstructor
public class AnnotationsBuilder {

	private static final String API_BASE_URL = "/";
	private static final String STRUCTURE_API = "structure";
	private static final String FUNCTION_API = "function";
	private static final String POPULATION_API = "population";

	//private VariationFetcher variationFetcher;
	private ProteinsFetcher proteinsFetcher;
	private PDBeFetcher pdbeFetcher;

	public void build(String accession, long genomicLocation, String variantAA, int isoformPostion, Map<String, List<Variant>> variantMap,
			InputParams params, IsoFormMapping.IsoFormMappingBuilder builder) {
		buildPopulationObservation(accession, isoformPostion, variantMap, params.isPop(), genomicLocation, builder);

		buildFunction(accession, isoformPostion, variantAA, params.isFun(), builder);

		buildStructure(accession, isoformPostion, params.isStr(), builder);
	}

	private void buildStructure(String accession, int isoformPostion, boolean isStructure,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isStructure) {
			List<PDBeStructureResidue> proteinStructure = pdbeFetcher.fetch(accession, isoformPostion);
			builder.proteinStructure(proteinStructure);
		} else {
			String uri = buildUri(STRUCTURE_API, accession, isoformPostion);
			builder.proteinStructureUri(uri);
		}
	}

	private void buildFunction(String accession, int isoformPostion, String variantAA, boolean isFunction,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isFunction) {
			Protein protein = proteinsFetcher.fetch(accession, isoformPostion, variantAA);
			builder.referenceFunction(protein);
		} else {
			String uri = buildUri(FUNCTION_API, accession, isoformPostion);
			if (variantAA != null && !variantAA.isEmpty()) {
				uri += "?variantAA=" + variantAA;
			}
			builder.referenceFunctionUri(uri);
		}
	}

	private void buildPopulationObservation(String accession, int isoformPostion, Map<String, List<Variant>> variantMap, boolean isVariation, long genomicLocation,
			IsoFormMapping.IsoFormMappingBuilder builder) {
		if (isVariation) {
			//List<Variation> variations = variationFetcher.fetch(accession, isoformPostion);
			List<Variant> variants = variantMap.get(accession+":"+isoformPostion);
			builder.populationObservations(new PopulationObservation(variants));
		} else {
			String uri = buildUri(accession, isoformPostion, genomicLocation);
			builder.populationObservationsUri(uri);
		}
	}

	private static String buildUri(String operation, String accession, int position) {
		return API_BASE_URL + operation + Constants.SLASH + accession + Constants.SLASH
			+ position;

	}

	private static String buildUri(String accession, int isoformPostion, long genomicLocation) {
		return API_BASE_URL + POPULATION_API + Constants.SLASH + accession + Constants.SLASH
			+ isoformPostion + "?genomicLocation=" + genomicLocation;
	}
}
