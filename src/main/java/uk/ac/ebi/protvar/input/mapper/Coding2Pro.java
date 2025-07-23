package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.HGVSCodingInput;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.*;

/**
 * HGVS coding dna format
 * e.g. NM_014630.3(ZNF592):c.3136G>A (p.Gly1046Arg)
 * Steps to process above input line:
 * 1. ignore between brackets - the gene name and protein substitution (we'll still parse and store them)
 * 2. extract refseq accession (should have NM_ prefix)
 * 3. map refseq accession to ensembl enst identifier (or uniprot acc)
 * 4. map coding dna into protein and codon position
 * 5. use uniprot acc + protein substitution (if available) as input
 *
 */
@Service
@AllArgsConstructor
public class Coding2Pro {

    public static List<String> getUniprotAccs(String refseqId, TreeMap<String, List<String>> refseqIdMap, UserInput input) {
        if (refseqIdMap.containsKey(refseqId))
            return refseqIdMap.get(refseqId);
        else {
            int dotIdx = refseqId.lastIndexOf(".");
            if (dotIdx != -1) {
                String idWithoutVersion = refseqId.substring(0, dotIdx);
                Map<String, List<String>> result = FetcherUtils.getByPrefix(refseqIdMap, idWithoutVersion);
                if (!result.isEmpty()) {
                    SortedSet<String> keys = new TreeSet<>(result.keySet());
                    String firstKey = keys.first();
                    input.addInfo(String.format(ErrorConstants.HGVS_USE_DIFF_REFSEQ_VERSION.toString(), firstKey));
                    return result.get(firstKey);
                }
            }
        }
        return List.of();
    }

    public void convert(Map<Type, List<UserInput>> groupedInputs, TreeMap<String, List<String>> refseqIdMap) {
        if (groupedInputs.containsKey(Type.CODING)) {
            List<UserInput> codingInputs = groupedInputs.get(Type.CODING);
            codingInputs.stream().map(i -> (HGVSCodingInput) i).forEach(cDNAProt -> {
                List<String> uniprotAccs = getUniprotAccs(cDNAProt.getRefseqId(),
                        refseqIdMap, cDNAProt);
                if (uniprotAccs != null && uniprotAccs.size() > 0) {
                    int[] protAndCodonPos = coding2ProteinPosition(cDNAProt.getPosition());
                    if (protAndCodonPos.length == 2) {
                        List<String> head = uniprotAccs.subList(0, 1);
                        List<String> tail = uniprotAccs.subList(1, uniprotAccs.size());

                        cDNAProt.setDerivedUniprotAcc(head.get(0));
                        cDNAProt.setDerivedProtPos(protAndCodonPos[0]);
                        cDNAProt.setDerivedCodonPos(protAndCodonPos[1]);
                        if (cDNAProt.getAaPos() != null && cDNAProt.getAaPos() != protAndCodonPos[0]) {
                            cDNAProt.addWarning(ErrorConstants.HGVS_C_POS_NOT_MATCHED);
                        }

                        if (tail != null && tail.size() > 1) {
                            cDNAProt.addWarning(String.format(ErrorConstants.HGVS_REFSEQ_MULTIPLE_PROTEINS.toString(),
                                    Arrays.toString(uniprotAccs.toArray()),
                                    head.get(0)));
                        }
                    }
                } else {
                    cDNAProt.addError(ErrorConstants.HGVS_REFSEQ_NO_PROTEIN);
                }
            });
        }
    }

    private int[] coding2ProteinPosition(int codingPosition) {

        int codonSize = 3;
        int quotient = codingPosition / codonSize;
        int remainder = codingPosition % codonSize;

        int proteinPos;
        int codonPos;
        if (remainder == 0) {
            proteinPos = quotient;
            codonPos = 3;
        } else {
            proteinPos = quotient + 1;
            codonPos = remainder;
        }
        return new int[] {proteinPos, codonPos};
    }
}
