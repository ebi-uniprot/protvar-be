package uk.ac.ebi.pepvep.fetcher.csv;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.model.api.*;
import uk.ac.ebi.pepvep.utils.FetcherUtils;
import uk.ac.ebi.pepvep.model.response.IsoFormMapping;
import uk.ac.ebi.pepvep.model.response.Protein;
import uk.ac.ebi.pepvep.utils.CSVUtils;

@Service
public class CSVFunctionDataFetcher {

	public List<String> fetch(IsoFormMapping mapping) {
		List<String> output = new ArrayList<>();
		Protein proteinFunction = mapping.getReferenceFunction();
		List<ProteinFeature> residueFeatures = proteinFunction.getFeatures().stream()
				.filter(feature -> !"VARIANTS".equalsIgnoreCase(feature.getCategory()))
				.filter(feature -> feature.getBegin() == feature.getEnd()).collect(Collectors.toList());
		output.add(CSVUtils.getValOrNA(buildProteinFeature(residueFeatures)));
		List<ProteinFeature> regionFeatures = proteinFunction.getFeatures().stream()
				.filter(feature -> !"VARIANTS".equalsIgnoreCase(feature.getCategory()))
				.filter(feature -> feature.getBegin() != feature.getEnd()).collect(Collectors.toList());
		output.add(CSVUtils.getValOrNA(buildProteinFeature(regionFeatures)));
		output.addAll(buildProteinDetails(proteinFunction));
		output.addAll(buildComments(proteinFunction.getComments()));
		return output;
	}

	private List<String> buildComments(List<DSPComment> comments) {
		if (comments == null)
			return List.of("N/A", "N/A", "N/A", "N/A", "N/A");
		StringJoiner catalyticActivity = new StringJoiner("|");
		StringJoiner complex = new StringJoiner("|");
		StringJoiner subCellularLocation = new StringJoiner("|");
		StringJoiner family = new StringJoiner("|");
		StringJoiner interactions = new StringJoiner("|");
		List<String> commentColumns = new ArrayList<>();
		for (DSPComment comment : comments) {
			switch (comment.getType()) {

			case "CATALYTIC_ACTIVITY":
				String activity = buildCatalyticActivity(comment);
				if (activity != null)
					catalyticActivity.add(activity);
				break;
			case "SUBUNIT":
				complex.add(buildComplex(comment));
				break;
			case "SUBCELLULAR_LOCATION":
				String location = buildSubCellularLocation(comment);
				if (location != null)
					subCellularLocation.add(location);
				break;
			case "SIMILARITY":
				family.add(buildFamily(comment));
				break;
			case "INTERACTION":
				String interaction = buildInteractions(comment);
				if (interaction != null)
					interactions.add(interaction);
				break;
			}

		}
		commentColumns.add(CSVUtils.getValOrNA(catalyticActivity.toString()));
		commentColumns.add(CSVUtils.getValOrNA(complex.toString()));
		commentColumns.add(CSVUtils.getValOrNA(subCellularLocation.toString()));
		commentColumns.add(CSVUtils.getValOrNA(family.toString()));
		commentColumns.add(CSVUtils.getValOrNA(interactions.toString()));
		return commentColumns;

	}

	private String buildInteractions(DSPComment comment) {
		if (comment.getInteractions() != null && !comment.getInteractions().isEmpty()) {
			return comment.getInteractions().stream().map(interaction ->
				interaction.getAccession2() + "(" + interaction.getGene() + ")"
			).collect(Collectors.joining(";"));
		}
		return null;
	}

	private String buildFamily(DSPComment comment) {
		if (comment.getText() != null && !comment.getText().isEmpty()) {
			if (comment.getText().get(0) instanceof Map) {
				Map<?,?> complexMap = ((Map<?,?>) comment.getText().get(0));
				String text = complexMap.get("value").toString();
				if (text != null && text.contains("."))
					return text.split("\\.")[0];
				return text;
			}
		}
		return null;
	}

	private String buildSubCellularLocation(DSPComment comment) {
		if (comment.getLocations() != null && !comment.getLocations().isEmpty()) {
			String location = comment.getLocations().stream().map(Locations::getLocation).map(Location::getValue)
					.collect(Collectors.joining(";"));
			String topologies = comment.getLocations().stream().map(Locations::getTopology).filter(Objects::nonNull)
					.map(Location::getValue).collect(Collectors.joining(";"));
			if (StringUtils.isNotEmpty(topologies))
				return location + "(" + topologies + ")";
			return location;
		}
		return null;
	}

	private String buildCatalyticActivity(DSPComment comment) {
		if (comment.getReaction() != null && comment.getReaction().getDbReferences() != null) {
			String rheaId = comment.getReaction().getDbReferences().stream()
					.filter(dbref -> dbref.getId().contains("RHEA:")).findFirst().map(DBReference::getId).orElse("");
			String evidence = FetcherUtils.evidencesToString(comment.getReaction().getEvidences());
			return rheaId + evidence;
		}
		return null;
	}

	private String buildComplex(DSPComment comment) {
		if (comment.getText() != null && !comment.getText().isEmpty()) {
			if (comment.getText().get(0) instanceof Map) {
				Map<?,?> complexMap = ((Map<?,?>) comment.getText().get(0));
				String text = complexMap.get("value").toString();
				if (text != null && text.contains("."))
					text = text.split("\\.")[0];
				return text;
			}
		}
		return null;

	}

	private String buildProteinFeature(List<ProteinFeature> proteinFeatures) {
		if (proteinFeatures == null || proteinFeatures.isEmpty())
			return "";

		StringJoiner joiner = new StringJoiner("|");
		proteinFeatures.forEach(feature -> {
			StringBuilder builder = new StringBuilder();
			builder.append(feature.getType());
			if (feature.getDescription() != null) {
				builder.append("-");
				builder.append(feature.getDescription());
			}
			if (feature.getBegin() != feature.getEnd()) {
				builder.append(";Range:").append(feature.getBegin()).append("-").append(feature.getEnd());
			}
			builder.append(FetcherUtils.evidencesToString(feature.getEvidences()));
			joiner.add(builder.toString());
		});
		return joiner.toString();
	}

	private List<String> buildProteinDetails(Protein proteinFunction) {
		List<String> proteinDetails = new ArrayList<>();
		proteinDetails.add(CSVUtils.getValOrNA(proteinFunction.getProteinExistence()));
		proteinDetails.add(CSVUtils.getValOrNA(String.valueOf(proteinFunction.getSequence().getLength())));
		proteinDetails.add(CSVUtils.getValOrNA(proteinFunction.getLastUpdated()));
		proteinDetails.add(CSVUtils.getValOrNA(proteinFunction.getSequence().getModified()));
		return proteinDetails;
	}
}
