package uk.ac.ebi.protvar.fetcher.csv;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.model.response.Isoform;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.utils.VariantKey;
import uk.ac.ebi.protvar.service.FunctionService;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.utils.CsvUtils;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.domain.entry.DbReference;
import uk.ac.ebi.uniprot.domain.entry.EvidencedString;
import uk.ac.ebi.uniprot.domain.entry.comment.CatalyticActivityComment;
import uk.ac.ebi.uniprot.domain.entry.comment.Comment;
import uk.ac.ebi.uniprot.domain.entry.comment.IntActComment;
import uk.ac.ebi.uniprot.domain.entry.comment.SubcellLocationComment;
import uk.ac.ebi.uniprot.domain.entry.comment.TextComment;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.uniprot.domain.features.FeatureCategory;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CsvFunctionDataBuilder {
	private final FunctionService functionService;

	/** The protein function columns (UniProt), in CsvHeaders order; empty when not requested or not found. */
	public List<String> build(Isoform isoform, AnnotationData annData) {
		if (!annData.isFun()) return Collections.emptyList();

		FunctionalInfo functionalInfo = functionService.get(isoform.getAccession(), isoform.getIsoformPosition());
		if (functionalInfo == null) return Collections.emptyList();

		List<String> output = new ArrayList<>();

		Map<Boolean, List<Feature>> partitionedFeatures = functionalInfo.getFeatures().stream()
				.filter(feature -> !feature.getCategory().equals(FeatureCategory.VARIANTS))
				.collect(Collectors.partitioningBy(feature -> feature.getBegin().equals(feature.getEnd())));

		output.add(CsvUtils.getValOrNA(buildProteinFeature(partitionedFeatures.get(true)))); // Residue_features
		output.add(CsvUtils.getValOrNA(buildProteinFeature(partitionedFeatures.get(false)))); // Region_features
		output.addAll(buildProteinDetails(functionalInfo)); // Existence_evidence, Protein_length, Entry_updated, Sequence_updated
		output.addAll(buildComments(functionalInfo.getComments())); // Catalytic_activity, Complex, Subcellular_location, Family, Interactions
		return output;
	}

	/** The prediction columns, in CsvHeaders order; empty when not requested. */
	public List<String> buildPredictions(Isoform isoform, AnnotationData annData) {
		if (!annData.isPred()) return Collections.emptyList();

		// Score/FoldX maps are keyed by the 1-letter variant AA (as stored), isoform.getVariantAA() is 3-letter
		// (e.g. "Thr"): normalise, or those lookups miss (N/A)
		String acc = isoform.getAccession();
		int pos = isoform.getIsoformPosition();
		String mt = AminoAcid.oneLetter(isoform.getVariantAA());
		String key = VariantKey.protein(acc, pos);
		List<Pocket> pockets = annData.getPocketMap() == null ? null : annData.getPocketMap().get(key);
		List<Interaction> interactions = annData.getInteractMap() == null ? null : annData.getInteractMap().get(key);
		List<Foldx> foldxs = annData.getFoldxMap() == null ? null : annData.getFoldxMap().get(VariantKey.protein(acc, pos, mt));
		ConservScore conserv = score(annData, VariantKey.protein(ScoreType.CONSERV, acc, pos, null), ConservScore.class);
		EsmScore esm = score(annData, VariantKey.protein(ScoreType.ESM, acc, pos, mt), EsmScore.class);
		M3DPred m3d = score(annData, VariantKey.protein(ScoreType.M3D, acc, pos, mt), M3DPred.class);

		List<String> output = new ArrayList<>();
		output.add(CsvUtils.getValOrNA(conserv == null ? null : conserv.getScore())); // Conservation
		output.add(CsvUtils.getValOrNA(esm == null ? null : esm.getScore())); // ESM1b
		output.add(CsvUtils.getValOrNA(buildFoldxPrediction(foldxs))); // FoldX
		output.add(CsvUtils.getValOrNA(missense3d(m3d))); // Missense3D
		output.add(CsvUtils.getValOrNA(buildPredictedPockets(pockets))); // Pockets
		output.add(CsvUtils.getValOrNA(buildPredictedInteractions(interactions))); // Interfaces
		return output;
	}

	// e.g. "Damaging(Buried Pro introduced)"
	static String missense3d(M3DPred m3d) {
		if (m3d == null || m3d.getPrediction() == null) return null;
		String feature = m3d.getDamagingFeature();
		return feature == null || feature.isBlank() || feature.equals("-")
				? m3d.getPrediction() : m3d.getPrediction() + "(" + feature + ")";
	}

	private static <T extends Score> T score(AnnotationData annData, String key, Class<T> type) {
		if (annData.getScoreMap() == null) return null;
		return annData.getScoreMap().getOrDefault(key, List.of()).stream()
				.filter(type::isInstance).map(type::cast).findFirst().orElse(null);
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
					String subunit = buildComplex(comment);
					if (subunit != null)
						complex.add(subunit);
					break;
				case SUBCELLULAR_LOCATION:
					String location = buildSubCellularLocation(comment);
					if (location != null)
						subCellularLocation.add(location);
					break;
				case SIMILARITY:
					String similarity = buildFamily(comment);
					if (similarity != null)
						family.add(similarity);
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

	// SUBUNIT/SIMILARITY comments deserialise to TextComment; first sentence of the first text
	private String extractValue(Comment comment) {
		if (comment instanceof TextComment textComment
				&& textComment.getText() != null && !textComment.getText().isEmpty()) {
			String textValue = textComment.getText().get(0).getValue();
			if (textValue == null) return null;
			return textValue.contains(".") ? textValue.split("\\.")[0] : textValue;
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
		var sequence = proteinFunction.getSequence();
		proteinDetails.add(CsvUtils.getValOrNA(proteinFunction.getProteinExistence()));
		proteinDetails.add(CsvUtils.getValOrNA(sequence == null ? null : String.valueOf(sequence.getLength())));
		proteinDetails.add(CsvUtils.getValOrNA(proteinFunction.getLastUpdated()));
		proteinDetails.add(CsvUtils.getValOrNA(sequence == null ? null : sequence.getModified()));
		return proteinDetails;
	}
}
