package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.UniprotEntryCache;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.repo.UniprotRefseqRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.RNACodon;

import java.util.*;
import java.util.stream.Collectors;
import static uk.ac.ebi.protvar.input.ErrorConstants.*;

@Service
@AllArgsConstructor
public class Pro2Gen {

    private ProtVarDataRepo protVarDataRepo;

    @Autowired
    UniprotEntryCache uniprotEntryCache;

    private UniprotRefseqRepo uniprotRefseqRepo;

    public void convert(Map<Type, List<UserInput>> groupedInputs, TreeMap<String, List<String>> rsAccsMap) {
        List<UserInput> proteinInputs = new ArrayList<>();
        if (groupedInputs.get(Type.PROTEIN) != null)
            proteinInputs.addAll(groupedInputs.get(Type.PROTEIN));
        if (groupedInputs.get(Type.CODING) != null)
            proteinInputs.addAll(groupedInputs.get(Type.CODING));
        if (!proteinInputs.isEmpty())
            convert(proteinInputs, rsAccsMap);
    }

    private void convert(List<UserInput> proteinInputs, TreeMap<String, List<String>> rsAccsMap) {
        // 1. get all the accessions and positions
        List<Object[]> accPosList = new ArrayList<>();
        for (UserInput input : proteinInputs) {

            if (input instanceof HGVSp) {
                HGVSp hgvsProt = (HGVSp) input;
                List<String> uniprotAccs = Coding2Pro.getUniprotAccs(hgvsProt.getRsAcc(), rsAccsMap, hgvsProt);
                if (uniprotAccs != null && uniprotAccs.size() > 0){
                    List<String> head =  uniprotAccs.subList(0, 1);
                    List<String> tail =  uniprotAccs.subList(1, uniprotAccs.size());

                    hgvsProt.setAcc(head.get(0));
                    accPosList.add(new Object[]{head.get(0), hgvsProt.getPos()});

                    if (tail != null) {
                        /*
                        if (tail.size() == 0) {
                            hgvsProt.addInfo(String.format(
                                    ErrorConstants.HGVS_REFSEQ_MAPPED_TO_PROTEIN.getErrorMessage(),
                                    hgvsProt.getAcc()));
                        }*/
                        if (tail.size() > 1) {
                            hgvsProt.addWarning(String.format(
                                    ErrorConstants.HGVS_REFSEQ_MULTIPLE_PROTEINS.getErrorMessage(),
                                    Arrays.toString(uniprotAccs.toArray()),
                                    head.get(0)));
                        }
                    }
                    if (!uniprotEntryCache.isValidEntry(hgvsProt.getAcc())) {
                        hgvsProt.addWarning(
                                String.format(ErrorConstants.HGVS_UNIPROT_ACC_NOT_FOUND.getErrorMessage()
                                , hgvsProt.getRsAcc(), hgvsProt.getAcc()));
                    }
                } else {
                    hgvsProt.addWarning(ErrorConstants.HGVS_REFSEQ_NO_PROTEIN);
                }

            } else if(input instanceof ProteinInput) { // custom Protein
                ProteinInput customProt = (ProteinInput) input;
                accPosList.add(new Object[]{customProt.getAcc(), customProt.getPos()});

                if (!uniprotEntryCache.isValidEntry(customProt.getAcc())) {
                    customProt.addError(String.format(ErrorConstants.PROT_UNIPROT_ACC_NOT_FOUND.toString(), customProt.getAcc()));
                }

            } else if (input instanceof HGVSc) {
                HGVSc cDNAProt = (HGVSc) input;
                if (cDNAProt.getDerivedUniprotAcc() != null) {/*
                    cDNAProt.addInfo(String.format(
                        ErrorConstants.HGVS_REFSEQ_MAPPED_TO_PROTEIN.getErrorMessage(),
                            cDNAProt.getDerivedUniprotAcc()));*/

                    if (!uniprotEntryCache.isValidEntry(cDNAProt.getDerivedUniprotAcc()))
                        cDNAProt.addWarning(
                                String.format(ErrorConstants.HGVS_UNIPROT_ACC_NOT_FOUND.getErrorMessage()
                                        , cDNAProt.getRsAcc(), cDNAProt.getDerivedUniprotAcc()));
                    if (cDNAProt.getDerivedProtPos() != null)
                        accPosList.add(new Object[]{cDNAProt.getDerivedUniprotAcc(), cDNAProt.getDerivedProtPos()});
                }
            }
        }

        // 2. get all the relevant db records by accessions and positions
        Map<String, List<GenomeToProteinMapping>> gCoords = protVarDataRepo.getMappingsByAccPos(accPosList)
                .stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupByProteinAccAndPos));

        // 3. we expect each protein input to have 3 genomic coordinates (in normal cases),
        //	which we will try to pin down to one, if possible, based on the user inputs
        proteinInputs.stream().filter(i -> i.isValid()).forEach(i -> {

            if (i instanceof HGVSp || i instanceof ProteinInput) {
                ProteinInput input = (ProteinInput) i;

                String key = Commons.joinWithDash(input.getAcc(), input.getPos());
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);
                // ^ all the genomic coordinates for protein accession and position

                Set<String> seen = new HashSet<>();

                // Forward strand example rows
                // from g2p_mapping tbl
                //                           allele in DNA   codon in RNA      A<->T
                //                              (ATCG)      / (AUCG)           C<->G
                //                                |        |                   |
                //                                v        v                   v
                // protein_position,protein_seq,allele,codon,codon_position,reverse_strand        alt_alleles
                //                1,          M,     A,  Aug,             1,         false        TCG (at codon pos 1)
                //                1,          M,     T,  aUg,             2,         false        ACG (at codon pos 2)
                //                1,          M,     G,  auG,             3,         false        ATC (at codon pos 3)
                //
                // Reverse strand example
                // protein_position,protein_seq,allele,codon,codon_position,reverse_strand        alt_alleles
                //                1,          M,     T,  Aug,             1,          true        ACG (at codon pos 1)
                //                1,          M,     A,  aUg,             2,          true        TCG (at codon pos 2)
                //                1,          M,     C,  auG,             3,          true        ATG (at codon pos 3)

                // iterations for each chr-pos-allele
                // chr - gen_coord1/codon_pos1 - allele - alt allele 1
                //                                        alt allele 2
                //                                        alt allele 3
                // chr - gen_coord2/codon_pos2 - allele - alt allele 1
                //                                        alt allele 2
                //                                        alt allele 3
                // chr - gen_coord3/codon_pos3 - allele - alt allele 1
                //                                        alt allele 2
                //                                        alt allele 3

                List<GenomicInput> altGInputs = new ArrayList<>();
                List<ErrorConstants> errors = new ArrayList<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        String gCoordChr = gCoord.getChromosome();
                        Integer gCoordPos = gCoord.getGenomeLocation();
                        String gCoordRefAllele = gCoord.getBaseNucleotide();

                        String curr = Commons.joinWithDash(gCoordChr, gCoordPos, gCoordRefAllele);
                        if (seen.contains(curr)) return;
                        seen.add(curr);

                        String gCoordAa = gCoord.getAa();
                        String gCoordCodon = gCoord.getCodon(); //should code for/translate into refAA
                        Integer gCoordCodonPos = gCoord.getCodonPosition();
                        Boolean gCoordIsReverse = gCoord.isReverseStrand();

                        Set<String> possibleAltAlleles = GenomicInput.getAlternates(gCoordRefAllele); // allele is in DNA letters -ATCG

                        AminoAcid gCoordRefAA = AminoAcid.fromOneOrThreeLetter(gCoordAa);

                        // both ref and alt not provided
                        if (input.getRef() == null && input.getAlt() == null) {
                            // Ref & var empty
                            if (!errors.contains(ERR_CODE_REF_EMPTY)) {
                                errors.add(ERR_CODE_REF_EMPTY);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        String.format(ERR_CODE_REF_EMPTY.getErrorMessage(), input.getPos(), gCoordRefAA.getThreeLetters())));
                            }

                            if (!errors.contains(ERR_CODE_VAR_EMPTY)) {
                                errors.add(ERR_CODE_VAR_EMPTY);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        ERR_CODE_VAR_EMPTY.getErrorMessage()));
                            }

                            for (String altAllele : possibleAltAlleles) {
                                //altAllele = gCoordIsReverse ? reverseDNA(altAllele) : altAllele;
                                GenomicInput gInput = new GenomicInput(input.getInputStr());
                                gInput.setChr(gCoordChr);
                                gInput.setPos(gCoordPos);
                                gInput.setRef(gCoordRefAllele);
                                gInput.setAlt(altAllele);
                                input.getDerivedGenomicInputs().add(gInput);
                            }
                        } else if (input.getRef() != null) { // ref provided

                            AminoAcid refAA = AminoAcid.fromOneOrThreeLetter(input.getRef());

                            // reference mismatch
                            if (!refAA.equals(gCoordRefAA) && !errors.contains(ERR_CODE_REF_MISMATCH)) {
                                errors.add(ERR_CODE_REF_MISMATCH);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        String.format(ERR_CODE_REF_MISMATCH.getErrorMessage(),
                                                refAA.getThreeLetters(), gCoordRefAA.getThreeLetters(), input.getPos(), gCoordRefAA.getThreeLetters())));
                                input.setRef(gCoordAa);
                            }

                            AminoAcid userAltAA = null;
                            if (input.getAlt() == null) { // alt not provided
                                if (!errors.contains(ERR_CODE_VAR_EMPTY)) {
                                    errors.add(ERR_CODE_VAR_EMPTY);
                                    input.getMessages().add(new Message(Message.MessageType.WARN,
                                            ERR_CODE_VAR_EMPTY.getErrorMessage()));
                                }
                            }
                            else {
                                userAltAA = AminoAcid.fromOneOrThreeLetter(input.getAlt());

                                // variant non SNV
                                if (!AminoAcid.isSNV(gCoordRefAA, userAltAA) && !errors.contains(ERR_CODE_VARIANT_NON_SNV)) {
                                    errors.add(ERR_CODE_VARIANT_NON_SNV);
                                    input.getMessages().add(new Message(Message.MessageType.WARN,
                                            String.format(ERR_CODE_VARIANT_NON_SNV.getErrorMessage(),
                                                    userAltAA.getThreeLetters(), gCoordRefAA.getThreeLetters())));
                                }
                            }

                            String codonUC = gCoordCodon.toUpperCase();

                            for (String altAllele : possibleAltAlleles) {

                                String altCodon = codonUC.substring(0, gCoordCodonPos - 1) +
                                        altAlleleIfReverse(altAllele, gCoordIsReverse) +
                                        codonUC.substring(gCoordCodonPos);

                                RNACodon altRnaCodon = RNACodon.valueOf(altCodon);
                                AminoAcid altAA = altRnaCodon.getAa();

                                GenomicInput gInput = new GenomicInput(input.getInputStr());
                                gInput.setChr(gCoordChr);
                                gInput.setPos(gCoordPos);
                                gInput.setRef(gCoordRefAllele);
                                gInput.setAlt(altAllele);

                                // filter out codon that doesn't code for user variant amino acid
                                if (userAltAA != null && !altAA.equals(userAltAA)) {
                                    altGInputs.add(gInput);
                                    continue; // check the next altAllele
                                }
                                input.getDerivedGenomicInputs().add(gInput);
                            }
                        }

                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty()) {
                    //if we haven't got a filtered set of genomic coordinates, return all possible combinations
                    if (altGInputs.isEmpty()) {
                        input.addError(PROT_NO_GEN_MAPPING);
                    }
                    else {
                        input.getDerivedGenomicInputs().addAll(altGInputs);
                    }
                }

            } else if (i instanceof HGVSc) {
                HGVSc input = (HGVSc) i;

                String key = Commons.joinWithDash(input.getDerivedUniprotAcc(), input.getDerivedProtPos());
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);

                Set<String> seen = new HashSet<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        String gCoordChr = gCoord.getChromosome();
                        Integer gCoordPos = gCoord.getGenomeLocation();
                        String gCoordRefAllele = gCoord.getBaseNucleotide();

                        String curr = Commons.joinWithDash(gCoordChr, gCoordPos, gCoordRefAllele);
                        if (seen.contains(curr)) return;
                        seen.add(curr);

                        Integer gCoordCodonPos = gCoord.getCodonPosition();
                        if (!gCoordCodonPos.equals(input.getDerivedCodonPos()))
                            return;

                        String cdnaRef = input.getRef();
                        String cdnaAlt = input.getAlt();

                        if (gCoord.isReverseStrand()) {
                            cdnaRef = reverseDNA(cdnaRef);
                            cdnaAlt = reverseDNA(cdnaAlt);
                        }

                        if (!gCoordRefAllele.equals(cdnaRef))
                            return;

                        GenomicInput gInput = new GenomicInput(input.getInputStr());
                        gInput.setChr(gCoordChr);
                        gInput.setPos(gCoordPos);
                        gInput.setRef(gCoordRefAllele);
                        gInput.setAlt(cdnaAlt);
                        input.getDerivedGenomicInputs().add(gInput);
                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty()) {
                    input.addError(CDNA_NO_GEN_MAPPING);
                }
            }
        });
    }

    private String altAlleleIfReverse(String altAllele, boolean isReverse) {
        //dna->rna codon
        if (isReverse) {
            //T->A
            //A->U
            //C->G
            //G->C
            if (altAllele.equals("T")) return "A";
            else if (altAllele.equals("A")) return "U";
            else if (altAllele.equals("C")) return "G";
            else if (altAllele.equals("G")) return "C";
        } else {
            //A->A
            //T->U
            //G->G
            //C->C
            if (altAllele.equals("T")) return "U";
        }
        return altAllele;
    }
    private String reverseDNA(String dna) {
        String reverseStr = "";
        for (char c : dna.toCharArray()) {
            if (c == 'A')
                reverseStr += 'T';
            else if (c == 'T')
                reverseStr += 'A';
            else if (c == 'C')
                reverseStr += 'G';
            else if (c == 'G')
                reverseStr += 'C';
        }
        return reverseStr;
    }

}
