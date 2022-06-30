package uk.ac.ebi.protvar.parser;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.utils.Chromosome2RefSeqId;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.ExtractUtils;

public class HGVSParser extends GenericParser {
    public static UserInput parse(String hgvs) {
        try {
            String refSeq = hgvs.split(":g")[0];
            String chromosome = Chromosome2RefSeqId.getChromosome(refSeq);
            Long startLoc = ExtractUtils.extractLocation(hgvs);
            String allele = ExtractUtils.extractAllele(hgvs, null);
            String[] alleles = allele.split(Constants.VARIANT_SEPARATOR);

            String ref = alleles[0];
            String alt = alleles[1];

            if (!VALID_ALLELES.contains(ref) || !VALID_ALLELES.contains(alt)) {
                throw new InvalidInputException("Invalid input : location");
            }

            UserInput input = new UserInput(UserInput.Type.HGVS);
            input.setChromosome(chromosome);
            input.setStart(startLoc);
            input.setRef(ref);
            input.setAlt(alt);

            return input;
        } catch (Exception ex) {
            return UserInput.invalidObject(hgvs, UserInput.Type.HGVS);
        }
    }
}
