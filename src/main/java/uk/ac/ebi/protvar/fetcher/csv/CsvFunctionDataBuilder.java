package uk.ac.ebi.protvar.fetcher.csv;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.mapper.FunctionalInfoEnricher;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.model.response.Isoform;
import uk.ac.ebi.protvar.model.score.EveScore;
import uk.ac.ebi.protvar.service.FunctionalAnnService;
import uk.ac.ebi.protvar.utils.CsvUtils;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.domain.entry.DbReference;
import uk.ac.ebi.uniprot.domain.entry.EvidencedString;
import uk.ac.ebi.uniprot.domain.entry.comment.CatalyticActivityComment;
import uk.ac.ebi.uniprot.domain.entry.comment.Comment;
import uk.ac.ebi.uniprot.domain.entry.comment.IntActComment;
import uk.ac.ebi.uniprot.domain.entry.comment.SubcellLocationComment;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.uniprot.domain.features.FeatureCategory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CsvFunctionDataBuilder {
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

	private final FunctionalAnnService functionalAnnService;
	private final FunctionalInfoEnricher functionalInfoEnricher;
	public List<String> build(Isoform isoform, AnnotationData annData) {
		if (!annData.isFun()) return Collections.emptyList();

		// functional info would have been preloaded in the cache
		FunctionalInfo functionalInfo = functionalAnnService.get(isoform.getAccession(), isoform.getIsoformPosition());
		if (functionalInfo == null) Collections.emptyList();

		functionalInfoEnricher.enrich(functionalInfo, annData, isoform.getVariantAA());

		List<String> output = new ArrayList<>();

		Map<Boolean, List<Feature>> partitionedFeatures = functionalInfo.getFeatures().stream()
				.filter(feature -> !feature.getCategory().equals(FeatureCategory.VARIANTS))
				.collect(Collectors.partitioningBy(feature -> feature.getBegin().equals(feature.getEnd())));

		List<Feature> residueFeatures = partitionedFeatures.get(true);
		List<Feature> regionFeatures = partitionedFeatures.get(false);

		output.add(CsvUtils.getValOrNA(buildProteinFeature(residueFeatures))); //Residue_function_(evidence)
		output.add(CsvUtils.getValOrNA(buildProteinFeature(regionFeatures))); //Region_function_(evidence)
		output.addAll(buildProteinDetails(functionalInfo)); //Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated
		output.addAll(buildComments(functionalInfo.getComments())); //Protein_catalytic_activity,Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene)
		output.add(CsvUtils.getValOrNA(buildPredictedPockets(functionalInfo.getPockets())));
		output.add(CsvUtils.getValOrNA(buildPredictedInteractions(functionalInfo.getInteractions())));
		output.add(CsvUtils.getValOrNA(buildFoldxPrediction(functionalInfo.getFoldxs())));
		output.add(CsvUtils.getValOrNA(functionalInfo.getConservScore() == null ? null : functionalInfo.getConservScore().getScore()));
		output.add(getAMScore(isoform));
		output.add(getEveScore(functionalInfo.getEveScore()));
		output.add(CsvUtils.getValOrNA(functionalInfo.getEsmScore() == null ? null : functionalInfo.getEsmScore().getScore()));
		return output;
	}

	private String getAMScore(Isoform isoform) {
		if (isoform.getAmScore() == null) return Constants.NA;
		return new StringBuilder()
				.append(isoform.getAmScore().getAmPathogenicity())
				.append("(")
				.append(isoform.getAmScore().getAmClass())
				.append(")").toString();
	}

	private String getEveScore(EveScore eveScore) {
		if (eveScore == null) return Constants.NA;
		return new StringBuilder()
				.append(eveScore.getScore())
				.append("(")
				.append(eveScore.getEveClass())
				.append(")").toString();
	}

	private List<String> buildComments(List<Comment> comments) {
		if (comments == null)
			return List.of("N/A", "N/A", "N/A", "N/A", "N/A");
		StringJoiner catalyticActivity = new StringJoiner("|");
		StringJoiner complex = new StringJoiner("|");
		StringJoiner subCellularLocation = new StringJoiner("|");
		StringJoiner family = new StringJoiner("|");
		StringJoiner interactions = new StringJoiner("|");
		List<String> commentColumns = new ArrayList<>();
		comments.forEach(comment -> {
			switch (comment.getType()) {

				case CATALYTIC_ACTIVITY:
					String activity = buildCatalyticActivity(comment);
					if (activity != null)
						catalyticActivity.add(activity);
					break;
				case SUBUNIT:
					complex.add(buildComplex(comment));
					break;
				case SUBCELLULAR_LOCATION:
					String location = buildSubCellularLocation(comment);
					if (location != null)
						subCellularLocation.add(location);
					break;
				case SIMILARITY:
					family.add(buildFamily(comment));
					break;
				case INTERACTION:
					String interaction = buildInteractions(comment);
					if (interaction != null)
						interactions.add(interaction);
					break;
			}
		});

		commentColumns.add(CsvUtils.getValOrNA(catalyticActivity.toString()));
		commentColumns.add(CsvUtils.getValOrNA(complex.toString()));
		commentColumns.add(CsvUtils.getValOrNA(subCellularLocation.toString()));
		commentColumns.add(CsvUtils.getValOrNA(family.toString()));
		commentColumns.add(CsvUtils.getValOrNA(interactions.toString()));
		return commentColumns;

	}

	private String buildInteractions(Comment comment) {
		if (comment instanceof IntActComment) {
			IntActComment intActComment = (IntActComment) comment;

			if (intActComment.getInteractions() != null && !intActComment.getInteractions().isEmpty()) {
				return intActComment.getInteractions().stream().map(interaction ->
						interaction.getAccession2() + "(" + interaction.getGene() + ")"
				).collect(Collectors.joining(";"));
			}
		}
		return null;
	}

	private String buildFamily(Comment comment) {
		return extractValue(comment);
	}

	private String extractValue(Comment comment) {
		if (comment != null) {
			Class<?> commentClass = comment.getClass();
			try {
				Field field = commentClass.getField("text");
				Object text = field.get(comment);
				if (text != null && text instanceof List) {
					List<?> textList = (List<?>) text;
					if (!textList.isEmpty()) {
						Object complex = textList.get(0);
						if (complex instanceof Map) {
							Map<?, ?> complexMap = (Map<?, ?>) complex;
							String textValue = complexMap.get("value").toString();
							return (textValue != null && textValue.contains(".")) ? textValue.split("\\.")[0] : textValue;
						}
					}
				}
			} catch (NoSuchFieldException e) {
				// ignore
			} catch (IllegalAccessException e) {
				// ignore
			}
		}
		return null;
	}

	private String buildSubCellularLocation(Comment comment) {
		if (comment instanceof SubcellLocationComment) {
			SubcellLocationComment subcellLocationComment = (SubcellLocationComment) comment;
			if (subcellLocationComment.getLocations() != null && !subcellLocationComment.getLocations().isEmpty()) {
				String location = subcellLocationComment.getLocations().stream().map(SubcellLocationComment.SubcellularLocation::getLocation).map(EvidencedString::getValue)
						.collect(Collectors.joining(";"));
				String topologies = subcellLocationComment.getLocations().stream().map(SubcellLocationComment.SubcellularLocation::getTopology).filter(Objects::nonNull)
						.map(EvidencedString::getValue).collect(Collectors.joining(";"));
				if (StringUtils.isNotEmpty(topologies))
					return location + "(" + topologies + ")";
				return location;
			}
		}
		return null;
	}

	private String buildCatalyticActivity(Comment comment) {
		if (comment instanceof CatalyticActivityComment) {
			CatalyticActivityComment catActComment = (CatalyticActivityComment) comment;
			if (catActComment.getReaction() != null && catActComment.getReaction().getDbReferences() != null) {
				String rheaId = catActComment.getReaction().getDbReferences().stream()
						.filter(dbref -> dbref.getId().contains("RHEA:"))
						.findFirst().map(DbReference::getId).orElse("");
				String evidences = FetcherUtils.evidencesToString(catActComment.getReaction().getEvidences());
				return rheaId + evidences;
			}
		}
		return null;
	}

	private String buildComplex(Comment comment) {
		return extractValue(comment);
	}

	private String buildProteinFeature(List<Feature> features) {
		if (features == null || features.isEmpty())
			return "";

		StringJoiner joiner = new StringJoiner("|");
		features.forEach(feature -> {
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


	private List<String> buildProteinDetails(FunctionalInfo proteinFunction) {
		List<String> proteinDetails = new ArrayList<>();
		proteinDetails.add(CsvUtils.getValOrNA(proteinFunction.getProteinExistence()));
		proteinDetails.add(CsvUtils.getValOrNA(String.valueOf(proteinFunction.getSequence().getLength())));
		proteinDetails.add(CsvUtils.getValOrNA(proteinFunction.getLastUpdated()));
		proteinDetails.add(CsvUtils.getValOrNA(proteinFunction.getSequence().getModified()));
		return proteinDetails;
	}
}
