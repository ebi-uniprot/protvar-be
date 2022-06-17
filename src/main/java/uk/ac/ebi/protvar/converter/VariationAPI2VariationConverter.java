package uk.ac.ebi.protvar.converter;

import java.util.List;

import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.exception.UnexpectedUseCaseException;
import uk.ac.ebi.protvar.model.api.DSVAssociation;
import uk.ac.ebi.protvar.model.api.Feature;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.utils.AminoAcid;

@Service
public class VariationAPI2VariationConverter {
	public Variation convert(Feature feature) {
		Variation variation = new Variation();

		variation.setGenomicLocation(feature.getGenomicLocation());
		variation.setCytogeneticBand(feature.getCytogeneticBand());
		variation.setWildType(threeLetterAminoAcid(feature.getWildType()));
		variation.setAlternativeSequence(threeLetterAminoAcid(feature.getAlternativeSequence()));
		variation.setBegin(feature.getBegin());
		variation.setEnd(feature.getEnd());
		variation.setPopulationFrequencies(feature.getPopulationFrequencies());
		variation.setAssociation(getAssociations(feature));
		variation.setEvidences(feature.getEvidences());
		variation.setPredictions(feature.getPredictions());
		variation.setXrefs(feature.getXrefs());
		variation.setClinicalSignificances(feature.getClinicalSignificances());
		return variation;
	}

	private List<DSVAssociation> getAssociations(Feature feature) {
		feature.getAssociation().forEach(association -> {
			if (association.getDescription() != null)
				association.setDescription(association.getDescription().replaceAll("α", "Alpha"));
		});
		return feature.getAssociation();
	}

	private String threeLetterAminoAcid(String letter) {
		//Problematic accessions: [P22681, Q13315, O60706, P50416, P14679, O75581, P46527, P49959, Q9BVK2, P02647, P01116,
		// O14521, O14686, Q8NFJ9, Q9UBM7, Q14432, Q9H3H5, Q13402, Q9Y210, P11217, Q14839, O00255, P38935, P11498,
		// Q68CP9, P07384, Q99959, P02458, Q71U36, P08397, P04275]

		//Alternative sequence values
		//null value can mean anything (might deletion, frameshift) but alternative sequence field is missing
		//RR, T*, QLQ, KRSHY* show insertions of new Amino Acids
		// - ? No idea why we have these values
		try {
			return AminoAcid.fromOneLetter(letter).getThreeLetters();
		} catch (UnexpectedUseCaseException ignore) {
			return null;
		}
	}

}
