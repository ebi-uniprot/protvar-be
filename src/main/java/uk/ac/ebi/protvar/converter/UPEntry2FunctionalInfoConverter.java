package uk.ac.ebi.protvar.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.uniprot.domain.entry.*;

@Service
public class UPEntry2FunctionalInfoConverter {

	public FunctionalInfo convert(UPEntry entry) {
		FunctionalInfo functionalInfo = new FunctionalInfo();
		// direct UPEntry to FunctionalInfo fields mapping
		functionalInfo.setAccession(entry.getAccession());
		functionalInfo.setId(entry.getId());
		functionalInfo.setProteinExistence(entry.getProteinExistence());
		functionalInfo.setGene(Commons.emptyOrList(entry.getGene()));
		functionalInfo.setComments(Commons.emptyOrList(entry.getComments()));
		functionalInfo.setFeatures(Commons.emptyOrList(entry.getFeatures()));
		functionalInfo.setDbReferences(filterDbReferences(entry.getDbReferences()));
		functionalInfo.setSequence(entry.getSequence());

		// processed fields
		// position?
		functionalInfo.setType(entry.getInfo().getType());
		functionalInfo.setName(buildName(entry.getProtein()));
		functionalInfo.setAlternativeNames(buildAlternativeName(entry.getProtein()));
		functionalInfo.setLastUpdated(entry.getInfo() == null ? null : entry.getInfo().getModified());

		return functionalInfo;
	}

	private List<DbReference> filterDbReferences(List<DbReference> dbReferences) {
		return dbReferences.stream()
				.filter(dbRef -> dbRef.getId() != null && ("InterPro".equalsIgnoreCase(dbRef.getType())
						|| "Pfam".equalsIgnoreCase(dbRef.getType()) || "CATH".equalsIgnoreCase(dbRef.getType())))
				.collect(Collectors.toList());
	}

	private String buildName(Protein protein) {
		if (protein != null && protein.getRecommendedName() != null && protein.getRecommendedName().getFullName() != null) {
			return protein.getRecommendedName().getFullName().getValue();
		}
		return null;
	}

	private String buildAlternativeName(Protein protein) {
		if (protein != null && protein.getAlternativeName() != null) {
			return protein.getAlternativeName().stream().map(ProteinName.Name::getFullName).map(EvidencedString::getValue)
					.collect(Collectors.joining(";"));
		}
		return null;
	}
}
