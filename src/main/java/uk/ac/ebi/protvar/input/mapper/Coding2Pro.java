package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.repo.UniprotRefseqRepo;

import java.util.*;
import java.util.stream.Collectors;

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
    UniprotRefseqRepo uniprotRefseqRepo;

    public void convert(Map<Type, List<UserInput>> groupedInputs) {
        if (groupedInputs.containsKey(Type.CODING)) {
            List<UserInput> codingInputs = groupedInputs.get(Type.CODING);

            Set<String> rsAccs = codingInputs.stream()
                    .map(i -> ((HGVSc) i).getRsAcc()).collect(Collectors.toSet());
            Map<String, List<String>> rsAccsMap = uniprotRefseqRepo.getRefSeqUniprotMap(rsAccs);

            codingInputs.stream().map(i -> (HGVSc) i).forEach(cDNAProt -> {


                List<String> uniprotAccs = rsAccsMap.get(cDNAProt.getRsAcc());
                if (uniprotAccs != null && uniprotAccs.size() > 0) {
                    int[] protAndCodonPos = coding2ProteinPosition(cDNAProt.getPos());
                    if (protAndCodonPos.length == 2) {
                        List<String> head = uniprotAccs.subList(0, 1);
                        List<String> tail = uniprotAccs.subList(1, uniprotAccs.size());

                        cDNAProt.setDerivedUniprotAcc(head.get(0));
                        cDNAProt.setDerivedProtPos(protAndCodonPos[0]);
                        cDNAProt.setDerivedCodonPos(protAndCodonPos[1]);
                        if (cDNAProt.getProtPos() != null && cDNAProt.getProtPos() != protAndCodonPos[0]) {
                            cDNAProt.addWarning(ErrorConstants.HGVS_C_POS_NOT_MATCHED);
                        }

                        if (tail != null && tail.size() > 1) {
                            cDNAProt.addWarning(String.format(ErrorConstants.HGVS_C_MULTIPLE_PROTEINS, Arrays.toString(uniprotAccs.toArray())));
                        }
                    }
                } else {
                    cDNAProt.addError(ErrorConstants.HGVS_C_RS_UP_NO_MAPPING);
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
