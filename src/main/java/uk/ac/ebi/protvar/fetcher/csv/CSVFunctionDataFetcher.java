package uk.ac.ebi.protvar.fetcher.csv;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.model.response.IsoFormMapping;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.protvar.utils.CSVUtils;
import uk.ac.ebi.uniprot.proteins.model.ProteinFeature;
import uk.ac.ebi.uniprot.proteins.model.DSPComment;
import uk.ac.ebi.uniprot.proteins.model.Location;
import uk.ac.ebi.uniprot.proteins.model.Locations;
import uk.ac.ebi.uniprot.proteins.model.DBReference;

@Service
public class CSVFunctionDataFetcher {
	//private static final String CSV_HEADER_OUTPUT_FUNCTION = "Residue_function_(evidence),Region_function_(evidence),"
	//	+ "Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,"
	//	+ "Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene)";
	// NEW
	// Predicted_pockets(energy;per_vol;score;resids),
	// Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ)
	// Foldx_prediction(foldxDdg;plddt)
	// Conservation_score
	// AlphaMissense
	// EVE
	// ESM
	public List<String> fetch(IsoFormMapping mapping) {
		List<String> output = new ArrayList<>();
		Protein proteinFunction = mapping.getReferenceFunction();
		List<ProteinFeature> residueFeatures = proteinFunction.getFeatures().stream()
				.filter(feature -> !"VARIANTS".equalsIgnoreCase(feature.getCategory()))
				.filter(feature -> feature.getBegin() == feature.getEnd()).collect(Collectors.toList());
		output.add(CSVUtils.getValOrNA(buildProteinFeature(residueFeatures))); //Residue_function_(evidence)
		List<ProteinFeature> regionFeatures = proteinFunction.getFeatures().stream()
				.filter(feature -> !"VARIANTS".equalsIgnoreCase(feature.getCategory()))
				.filter(feature -> feature.getBegin() != feature.getEnd()).collect(Collectors.toList());
		output.add(CSVUtils.getValOrNA(buildProteinFeature(regionFeatures))); //Region_function_(evidence)
		output.addAll(buildProteinDetails(proteinFunction)); //Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated
		output.addAll(buildComments(proteinFunction.getComments())); //Protein_catalytic_activity,Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene)
		output.add(CSVUtils.getValOrNA(buildPredictedPockets(proteinFunction.getPockets())));
		output.add(CSVUtils.getValOrNA(buildPredictedInteractions(proteinFunction.getInteractions())));
		output.add(CSVUtils.getValOrNA(buildFoldxPrediction(proteinFunction.getFoldxs())));
		output.add(CSVUtils.getValOrNA(mapping.getConservScore() == null ? null : mapping.getConservScore().getScore()));
		output.add(getAMScore(mapping));
		output.add(getEveScore(mapping));
		output.add(CSVUtils.getValOrNA(mapping.getEsmScore() == null ? null : mapping.getEsmScore().getScore()));
		return output;
	}

	private String getAMScore(IsoFormMapping mapping) {
		if (mapping.getAmScore() == null) return Constants.NA;
		return new StringBuilder()
				.append(mapping.getAmScore().getAmPathogenicity())
				.append("(")
				.append(mapping.getAmScore().getAmClass())
				.append(")").toString();
	}

	private String getEveScore(IsoFormMapping mapping) {
		if (mapping.getEveScore() == null) return Constants.NA;
		return new StringBuilder()
				.append(mapping.getEveScore().getScore())
				.append("(")
				.append(mapping.getEveScore().getEveClass())
				.append(")").toString();
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

	private String buildPredictedPockets(List<Pocket> pockets) {
		if (pockets == null || pockets.isEmpty())
			return "";

		StringJoiner joiner = new StringJoiner("|");
		pockets.forEach(pocket -> {
			StringBuilder builder = new StringBuilder();
			builder.append("pocket_id:").append(pocket.getPocketId());
			builder.append(";rad_gyration:").append(pocket.getRadGyration());
			builder.append(";energy_per_vol:").append(pocket.getEnergyPerVol());
			builder.append(";buriedness:").append(pocket.getBuriedness());
			builder.append(";resid:").append(formatIntegers(pocket.getResid()));
			builder.append(";mean_plddt:").append(pocket.getMeanPlddt());
			builder.append(";score:").append(pocket.getScore());
			joiner.add(builder.toString());
		});
		return joiner.toString();
	}

	private String buildPredictedInteractions(List<Interaction> interactions) {
		if (interactions == null || interactions.isEmpty())
			return "";

		StringJoiner joiner = new StringJoiner("|");
		interactions.forEach(interaction -> {

			StringBuilder builder = new StringBuilder();
			builder.append(interaction.getA()).append("-").append(interaction.getB());
			builder.append(";A residues:").append(formatIntegers(interaction.getAresidues()));
			builder.append(";B residues:").append(formatIntegers(interaction.getBresidues()));
			builder.append(";pDockQ:").append(interaction.getPdockq());
			joiner.add(builder.toString());
		});
		return joiner.toString();
	}

	private String formatIntegers(List<Integer> numbers) {
		if (numbers == null || numbers.isEmpty()) {
			return "";
		}

		Collections.sort(numbers);

		StringBuilder result = new StringBuilder();
		int startRange = numbers.get(0);
		int endRange = startRange;

		for (int i = 1; i < numbers.size(); i++) {
			if (numbers.get(i) == endRange + 1) {
				endRange = numbers.get(i);
			} else {
				if (startRange == endRange) {
					result.append(startRange).append(" ");
				} else {
					result.append(startRange).append("-").append(endRange).append(" ");
				}
				startRange = numbers.get(i);
				endRange = startRange;
			}
		}

		// Handle the last range
		if (startRange == endRange) {
			result.append(startRange);
		} else {
			result.append(startRange).append("-").append(endRange);
		}

		return result.toString();
	}

	private String getResids(List<Integer> resids) {
		if (resids == null || resids.isEmpty())
			return "-";
		return resids.stream().map(String::valueOf).collect(Collectors.joining(" "));
	}

	private String buildFoldxPrediction(List<Foldx> foldxs) {
		if (foldxs == null || foldxs.isEmpty())
			return "";
		StringJoiner joiner = new StringJoiner("|");
		foldxs.forEach(foldx -> {
			StringBuilder builder = new StringBuilder();
			builder.append("foldxDdg:").append(foldx.getFoldxDdg());
			builder.append(";plddt:").append(foldx.getPlddt());
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
