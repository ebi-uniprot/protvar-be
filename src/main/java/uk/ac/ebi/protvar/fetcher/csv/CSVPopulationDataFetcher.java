package uk.ac.ebi.protvar.fetcher.csv;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.model.api.DSVDbReferenceObject;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.utils.ExtractUtils;

import static uk.ac.ebi.protvar.utils.CSVUtils.getValOrNA;

@Service
public class CSVPopulationDataFetcher {

	public List<String> fetch(PopulationObservation populationObservations, String refAA, String variantAA,
			Long genomicLocation) {
		List<Variation> variants = populationObservations.getProteinColocatedVariant().stream()
				.filter(feature -> refAA.equalsIgnoreCase(feature.getWildType())
						&& variantAA.equalsIgnoreCase(feature.getAlternativeSequence())
						&& genomicLocation == getLocation(feature.getGenomicLocation()))
				.collect(Collectors.toList());

		List<Variation> colocatedVariants = populationObservations.getProteinColocatedVariant().stream()
				.filter(feature -> genomicLocation != getLocation(feature.getGenomicLocation()))
				.collect(Collectors.toList());
		List<String> csv = new ArrayList<>();
		if (variants.isEmpty()) {
			csv.addAll(List.of(Constants.NA, Constants.NA, Constants.NA, Constants.NA));
		} else {
			variants.forEach(variation -> {
				csv.addAll(csvOutput(variation));
				csv.add(getValOrNA(addDisease(variation)));
			});
		}
		if (colocatedVariants.isEmpty()) {
			csv.add(Constants.NA);
		} else {
			StringBuilder builder = new StringBuilder();
			colocatedVariants.forEach(var -> builder.append(colocatedVariant(var)));
			csv.add(builder.toString());
		}
		return csv;
	}

	private String addDisease(Variation variation) {
		StringJoiner diseaseJoiner = new StringJoiner("|");
		if (variation != null && variation.getAssociation() != null && !variation.getAssociation().isEmpty()) {
			variation.getAssociation().forEach(
					disease -> diseaseJoiner.add(disease.getName() + "-" + FetcherUtils.evidencesToString(disease.getEvidences())));
			return diseaseJoiner.toString();
		}
		return "";
	}

	private String colocatedVariant(Variation variation) {
		StringJoiner colocatedVariants = new StringJoiner("");
		colocatedVariants.add("[");
		colocatedVariants.add(variation.getWildType() + ">" + variation.getAlternativeSequence());
		colocatedVariants.add(";");
		colocatedVariants.add(variation.getGenomicLocation());
		colocatedVariants.add(";");
		colocatedVariants.add(variation.getCytogeneticBand());
		colocatedVariants.add(";");
		colocatedVariants.add(addIdentifiers(variation));
		colocatedVariants.add("]");
		return colocatedVariants.toString();
	}

	private List<String> csvOutput(Variation variation) {
		List<String> csvOutput = new ArrayList<>();
		csvOutput.add(getValOrNA(variation.getGenomicLocation()));
		csvOutput.add(getValOrNA(variation.getCytogeneticBand()));
		csvOutput.add(getValOrNA(addIdentifiers(variation)));
		return csvOutput;
	}

	private String addIdentifiers(Variation variation) {
		Map<String, String> clinicalSignificances = new HashMap<>();
		Map<String, String> populationFrequencies = new HashMap<>();
		variation.getClinicalSignificances().forEach(sig -> {
			String type = sig.getType();
			sig.getSources().forEach(source -> clinicalSignificances.put(source, type));
		});

		variation.getPopulationFrequencies().forEach(pop ->
			populationFrequencies.put(pop.getSource(), pop.getPopulationName() + ":" + pop.getFrequency())
		);
		StringJoiner variantDetails = new StringJoiner("|");
		variation.getXrefs().forEach(
				xref -> variantDetails.add(getVariantDetails(xref, clinicalSignificances, populationFrequencies)));
		return variantDetails.toString();
	}

	private String getVariantDetails(DSVDbReferenceObject xref, Map<String, String> clinicalSignificances,
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

	private long getLocation(String genomicLocation) {
		if (genomicLocation != null) {
			return ExtractUtils.extractLocation(genomicLocation);
		}
		return 0L;
	}

}
