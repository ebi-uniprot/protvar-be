package uk.ac.ebi.protvar.builder;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.fetcher.ProteinsFetcher;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.utils.Constants;
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
	private StructureService structureService;

	public void build(String accession, long genomicLocation, String variantAA, int isoformPostion,
					  Map<String, List<Variant>> variantsMap,
					  Map<String, List<Pocket>> pocketsMap,
					  Map<String, List<Interaction>> interactionsMap,
					  Map<String, List<Foldx>> foldxsMap,
					  InputParams params,
					  Isoform.IsoformBuilder builder) {
		buildPopulationObservation(accession, isoformPostion, variantsMap, params.isPop(), genomicLocation, builder);

		buildFunction(accession, isoformPostion, variantAA, pocketsMap, interactionsMap, foldxsMap, params.isFun(), builder);

		buildStructure(accession, isoformPostion, params.isStr(), builder);
	}

	private void buildStructure(String accession, int isoformPostion, boolean isStructure,
			Isoform.IsoformBuilder builder) {
		if (isStructure) {
			List<StructureResidue> structures = structureService.getStrFromCache(accession, isoformPostion);
			builder.proteinStructure(structures);
		} else {
			String uri = buildUri(STRUCTURE_API, accession, isoformPostion);
			builder.proteinStructureUri(uri);
		}
	}

	private void buildFunction(String accession, int isoformPostion, String variantAA,
							   Map<String, List<Pocket>> pocketsMap,
							   Map<String, List<Interaction>> interactionsMap,
							   Map<String, List<Foldx>> foldxsMap,
							   boolean isFunction,
							   Isoform.IsoformBuilder builder) {
		if (isFunction) {
			FunctionalInfo protein = proteinsFetcher.fetch(accession, isoformPostion, variantAA);
			// Add novel predictions
			var key = accession + "-" + isoformPostion;
			if (pocketsMap != null) {
				protein.setPockets(pocketsMap.get(key));
			}
			if (interactionsMap != null) {
				protein.setInteractions(interactionsMap.get(key));
			}
			var foldxKey = accession + "-" + isoformPostion + "-" + variantAA;
			if (foldxsMap != null) {
				protein.setFoldxs(foldxsMap.get(foldxKey));
			}
			builder.referenceFunction(protein);
		} else {
			String uri = buildUri(FUNCTION_API, accession, isoformPostion);
			if (variantAA != null && !variantAA.isEmpty()) {
				uri += "?variantAA=" + variantAA;
			}
			builder.referenceFunctionUri(uri);
		}
	}

	private void buildPopulationObservation(String accession, int isoformPostion, Map<String, List<Variant>> variantsMap, boolean isVariation, long genomicLocation,
			Isoform.IsoformBuilder builder) {
		if (isVariation) {
			//List<Variation> variations = variationFetcher.fetch(accession, isoformPostion);
			List<Variant> variants = variantsMap.get(accession+":"+isoformPostion);
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
