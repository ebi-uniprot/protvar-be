package uk.ac.ebi.protvar.input;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.utils.Chromosome2RefSeqId;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.ExtractUtils;

public class HGVSInput extends GenomicInput {

    public static final String HGVS_PREFIX = "NC_";

    public HGVSInput(String inputStr) {
        try {
            String refSeq = inputStr.split(":g")[0];
            String chromosome = Chromosome2RefSeqId.getChromosome(refSeq);
            Long startLoc = ExtractUtils.extractLocation(inputStr);
            String allele = ExtractUtils.extractAllele(inputStr, null);
            String[] alleles = allele.split(Constants.VARIANT_SEPARATOR);

            String ref = alleles[0];
            String alt = alleles[1];

            if (!GenomicInput.VALID_ALLELES.contains(ref) || !GenomicInput.VALID_ALLELES.contains(alt)) {
                throw new InvalidInputException("Invalid input : location");
            }

            this.chr = chromosome;
            this.pos = startLoc;
            this.ref = ref;
            this.alt = alt;

        } catch (InvalidInputException ex) {
            this.addInvalidReason("Error parsing HGVS input string");
        }
    }

    public InputType.Gen getGenType() {
        return InputType.Gen.HGVS;
    }
}
