package uk.ac.ebi.protvar.input.format.genomic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.types.RefseqChr;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HGVSg extends GenomicInput {
    @JsonIgnore @Getter @Setter
    Boolean rsAcc37;
    public HGVSg(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_GEN);
    }
}
