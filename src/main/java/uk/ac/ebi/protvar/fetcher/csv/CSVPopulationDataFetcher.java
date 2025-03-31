package uk.ac.ebi.protvar.fetcher.csv;

import java.util.*;

import org.springframework.stereotype.Service;

//import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.uniprot.domain.features.DbReferenceObject;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import static uk.ac.ebi.protvar.utils.CSVUtils.getValOrNA;

@Service
public class CSVPopulationDataFetcher {

	public List<String> fetch(PopulationObservation populationObservations, String refAA, String variantAA,
			Integer genomicLocation) {
		List<Variant> variants = new ArrayList<>();
		List<Variant> colocatedVariants = new ArrayList<>();
		if (populationObservations.getVariants() != null) {
			populationObservations.getVariants().forEach(v -> {
				//Integer featureGenLocation = HGVSg.extractLocation(feature.getGenomicLocation());
				/*if (genomicLocation.equals(featureGenLocation)) {
					if (refAA.equalsIgnoreCase(feature.getWildType()) &&
							variantAA.equalsIgnoreCase(feature.getAlternativeSequence())) {
						variants.add(feature);
					}
				}*/
				// Tmp fix to align downloaded file with what is displayed in the UI
				if (variantAA.equalsIgnoreCase(v.getAlternativeSequence())) {
					variants.add(v);
				} else {
					colocatedVariants.add(v);
				}
			});
		}

		List<String> csv = new ArrayList<>();
		if (variants.isEmpty()) {
			csv.addAll(List.of(Constants.NA, Constants.NA, Constants.NA, Constants.NA));
		} else {
			variants.forEach(v -> {
				csv.addAll(csvOutput(v));
				csv.add(getValOrNA(addDisease(v)));
			});
		}
		if (colocatedVariants.isEmpty()) {
			csv.add(Constants.NA);
		} else {
			StringBuilder builder = new StringBuilder();
			colocatedVariants.forEach(v -> builder.append(colocatedVariant(v)));
			csv.add(builder.toString());
		}
		return csv;
	}

	private String addDisease(Variant variant) {
		StringJoiner diseaseJoiner = new StringJoiner("|");
		if (variant != null && variant.getAssociation() != null && !variant.getAssociation().isEmpty()) {
			variant.getAssociation().forEach(
					disease -> diseaseJoiner.add(disease.getName() + "-" + FetcherUtils.evidencesToString(disease.getEvidences())));
			return diseaseJoiner.toString();
		}
		return "";
	}

	private String colocatedVariant(Variant variant) {
		StringJoiner colocatedVariants = new StringJoiner("");
		colocatedVariants.add("[");
		colocatedVariants.add(variant.getWildType() + ">" + variant.getAlternativeSequence());
		colocatedVariants.add(";");
		colocatedVariants.add(getValOrNA(variant.getGenomicLocation()));
		colocatedVariants.add(";");
		colocatedVariants.add(variant.getCytogeneticBand());
		colocatedVariants.add(";");
		colocatedVariants.add(addIdentifiers(variant));
		colocatedVariants.add("]");
		return colocatedVariants.toString();
	}

	private List<String> csvOutput(Variant variant) {
		List<String> csvOutput = new ArrayList<>();
		csvOutput.add(getValOrNA(variant.getGenomicLocation()));
		csvOutput.add(getValOrNA(variant.getCytogeneticBand()));
		csvOutput.add(getValOrNA(addIdentifiers(variant)));
		return csvOutput;
	}

	private String addIdentifiers(Variant variant) {
		Map<String, String> clinicalSignificances = new HashMap<>();
		Map<String, String> populationFrequencies = new HashMap<>();
		variant.getClinicalSignificances().forEach(sig -> {
			String type = sig.getType();
			sig.getSources().forEach(source -> clinicalSignificances.put(source, type));
		});

		variant.getPopulationFrequencies().forEach(pop ->
			populationFrequencies.put(pop.getSource(), pop.getPopulationName() + ":" + pop.getFrequency())
		);
		StringJoiner variantDetails = new StringJoiner("|");
		variant.getXrefs().forEach(
				xref -> variantDetails.add(getVariantDetails(xref, clinicalSignificances, populationFrequencies)));
		return variantDetails.toString();
	}

	private String getVariantDetails(DbReferenceObject xref, Map<String, String> clinicalSignificances,
									 Map<String, String> populationFrequencies) {
		String colocatedVar = xref.getName() + "-" + xref.getId();
		if (populationFrequencies.containsKey(xref.getName())) {
			colocatedVar = colocatedVar + ";" + populationFrequencies.get(xref.getName());
		}

		if (clinicalSignificances.containsKey(xref.getName())) {
			colocatedVar = colocatedVar + ";" + clinicalSignificances.get(xref.getName());
		}
		return colocatedVar;
	}

}
