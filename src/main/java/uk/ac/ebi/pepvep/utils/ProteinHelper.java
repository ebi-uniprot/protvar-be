package uk.ac.ebi.pepvep.utils;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ebi.pepvep.model.api.ProteinFeature;

public class ProteinHelper {

	public static List<ProteinFeature> filterFeatures(List<ProteinFeature> features, long begin, long end) {
		if (features != null && begin != 0L) {
			List<ProteinFeature> proteinFeatures = new ArrayList<>();
			features.forEach(proteinFeature -> {
				if (isWithinLocationRange(begin, end, proteinFeature)) {
					proteinFeature.setTypeDescription(ProteinType2Description.getDescription(proteinFeature.getType()));
					proteinFeatures.add(proteinFeature);
				}
			});
			return proteinFeatures;
		}
		return features;
	}

	private static boolean isWithinLocationRange(long begin, long end, ProteinFeature feature) {
		return (begin >= feature.getBegin() && end <= feature.getEnd())
				|| (begin >= feature.getBegin() && begin <= feature.getEnd())
				|| (end >= feature.getBegin() && end <= feature.getEnd());
	}
}
