package uk.ac.ebi.protvar.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.model.response.GeneName;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.uniprot.proteins.model.DBReference;
import uk.ac.ebi.uniprot.proteins.model.DSPName;
import uk.ac.ebi.uniprot.proteins.model.DSPRecommendedName;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.uniprot.proteins.model.ProteinGene;

@Service
public class ProteinsAPI2ProteinConverter {

	private static final Logger logger = LoggerFactory.getLogger(ProteinsAPI2ProteinConverter.class);

	public Protein fetch(DataServiceProtein dsp) {
		logger.info("Processing accession: {}", dsp.getAccession());
		Protein protein = new Protein();
		protein.setAccession(dsp.getAccession());
		protein.setName(buildName(dsp.getProtein().getRecommendedName()));
		protein.setAlternativeNames(buildAlternativeName(dsp.getProtein().getAlternativeName()));
		protein.setId(dsp.getId());
		protein.setProteinExistence(dsp.getProteinExistence());
		protein.setSequence(dsp.getSequence());
		protein.setType(dsp.getInfo().getType());
		protein.setFeatures(Commons.emptyOrList(dsp.getFeatures()));
		protein.setComments(dsp.getComments());
		protein.setGeneNames(getGeneName(dsp.getGene()));
		protein.setLastUpdated(dsp.getInfo().getModified());
		protein.setDbReferences(filterDbReferences(dsp.getDbReferences()));
		return protein;
	}

	private List<GeneName> getGeneName(List<ProteinGene> genes) {
		if(genes == null) return null;
		return genes.stream().map(gene -> {
			GeneName geneName = new GeneName();
			geneName.setGeneName(gene.getName() != null ? gene.getName().getValue() : null);
			if (gene.getSynonyms() != null && !gene.getSynonyms().isEmpty())
				geneName.setSynonyms(
						gene.getSynonyms().stream().map(DSPName::getValue).collect(Collectors.joining(",")));
			return geneName;
		}).collect(Collectors.toList());
	}

	private String buildAlternativeName(List<DSPRecommendedName> alternativeNames) {
		if (alternativeNames != null && alternativeNames.size() > 0) {
			return alternativeNames.stream().map(DSPRecommendedName::getFullName).map(DSPName::getValue)
					.collect(Collectors.joining(";"));
		}
		return null;
	}

	private List<DBReference> filterDbReferences(List<DBReference> dbReferences) {
		return dbReferences.stream()
				.filter(dbRef -> dbRef.getId() != null && ("InterPro".equalsIgnoreCase(dbRef.getType())
						|| "Pfam".equalsIgnoreCase(dbRef.getType()) || "CATH".equalsIgnoreCase(dbRef.getType())))
				.collect(Collectors.toList());
	}

	private String buildName(DSPRecommendedName recommendedName) {
		if (recommendedName != null) {
			return recommendedName.getFullName().getValue();
		}
		return null;
	}
}
