package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.UniprotEntryCache;
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

    public void convert(Map<Type, List<UserInput>> groupedInputs) {
        List<UserInput> proteinInputs = new ArrayList<>();
        if (groupedInputs.get(Type.PROTEIN) != null)
            proteinInputs.addAll(groupedInputs.get(Type.PROTEIN));
        if (groupedInputs.get(Type.CODING) != null)
            proteinInputs.addAll(groupedInputs.get(Type.CODING));
        if (!proteinInputs.isEmpty())
            convert(proteinInputs);
    }

    private void convert(List<UserInput> proteinInputs) {
        // 1. get all the accessions and positions
        Set<Object[]> accPosSet = new HashSet<>();

        Set<String> rsAccs = proteinInputs.stream().filter(i -> i instanceof HGVSp)
                .map(i -> ((HGVSp) i).getRsAcc()).collect(Collectors.toSet());
        Map<String, List<String>> rsAccsMap = uniprotRefseqRepo.getRefSeqUniprotMap(rsAccs);

        for (UserInput input : proteinInputs) {

            if (input instanceof HGVSp) {
                HGVSp hgvsProt = (HGVSp) input;
                List<String> uniprotAccs = rsAccsMap.get(hgvsProt.getRsAcc());
                if (uniprotAccs != null && uniprotAccs.size() > 0){
                    List<String> head =  uniprotAccs.subList(0, 1);
                    List<String> tail =  uniprotAccs.subList(1, uniprotAccs.size());

                    hgvsProt.setAcc(head.get(0));
                    accPosSet.add(new Object[]{head.get(0), hgvsProt.getPos()});

                    if (tail != null && tail.size() > 1) {
                        hgvsProt.addWarning(String.format("RefSeq id mapped to multiple Uniprot accessions: %s. ProtVar will use %s.", Arrays.toString(uniprotAccs.toArray()), hgvsProt.getAcc()));
                    }
                    if (!uniprotEntryCache.isValidEntry(hgvsProt.getAcc())) {
                        hgvsProt.addWarning("Invalid mapped accession " + hgvsProt.getAcc());
                    }
                } else {
                    hgvsProt.addWarning("Could not map RefSeq ID to a Uniprot protein");
                }

            } else if(input instanceof ProteinInput) { // custom Protein
                ProteinInput customProt = (ProteinInput) input;
                accPosSet.add(new Object[]{customProt.getAcc(), customProt.getPos()});

                if (!uniprotEntryCache.isValidEntry(customProt.getAcc())) {
                    customProt.addWarning("Invalid accession " + customProt.getAcc());
                }

            } else if (input instanceof HGVSc) {
                HGVSc cDNAProt = (HGVSc) input;
                if (cDNAProt.getDerivedUniprotAcc() != null) {
                    if (!uniprotEntryCache.isValidEntry(cDNAProt.getDerivedUniprotAcc()))
                        cDNAProt.addWarning("Invalid mapped accession " + cDNAProt.getDerivedUniprotAcc());
                    if (cDNAProt.getDerivedProtPos() != null)
                        accPosSet.add(new Object[]{cDNAProt.getDerivedUniprotAcc(), cDNAProt.getDerivedProtPos()});
                }
            }
        }

        // 2. get all the relevant db records by accessions and positions
        Map<String, List<GenomeToProteinMapping>> gCoords = protVarDataRepo.getMappingsByAccPos(accPosSet)
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
                Map<Integer, Message> errorMap = new TreeMap<>(); // order of message matters

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

                        Set<String> possibleAltAlleles = getAlternatesDNA(gCoordRefAllele); // allele is in DNA letters -ATCG

                        AminoAcid gCoordRefAA = AminoAcid.fromOneOrThreeLetter(gCoordAa);

                        if (input.getRef() == null && input.getAlt() == null) {
                            // Ref & var empty
                            if (!errorMap.containsKey(ERR_CODE_REF_EMPTY))
                                errorMap.put(ERR_CODE_REF_EMPTY,
                                        new Message(Message.MessageType.WARN,
                                                String.format(ERROR_MESSAGE.get(ERR_CODE_REF_EMPTY), input.getPos(), gCoordRefAA.getThreeLetters())));

                            if (!errorMap.containsKey(ERR_CODE_VAR_EMPTY))
                                errorMap.put(ERR_CODE_VAR_EMPTY,
                                        new Message(Message.MessageType.WARN, ERROR_MESSAGE.get(ERR_CODE_VAR_EMPTY)));

                            for (String altAllele : possibleAltAlleles) {
                                altAllele = gCoordIsReverse ? reverseDNA(altAllele) : altAllele;
                                GenomicInput gInput = new GenomicInput(input.getInputStr());
                                gInput.setChr(gCoordChr);
                                gInput.setPos(gCoordPos);
                                gInput.setRef(gCoordRefAllele);
                                gInput.setAlt(altAllele);
                                input.getDerivedGenomicInputs().add(gInput);
                            }
                        } else if (input.getRef() != null) {

                            AminoAcid refAA = AminoAcid.fromOneOrThreeLetter(input.getRef());

                            // reference mismatch
                            if (!refAA.equals(gCoordRefAA) && !errorMap.containsKey(ERR_CODE_REF_MISMATCH)) {
                                errorMap.put(ERR_CODE_REF_MISMATCH,
                                        new Message(Message.MessageType.WARN,
                                                String.format(ERROR_MESSAGE.get(ERR_CODE_REF_MISMATCH),
                                                        refAA.getThreeLetters(), gCoordRefAA.getThreeLetters(), input.getPos(), gCoordRefAA.getThreeLetters())));
                                input.setRef(gCoordAa);
                            }

                            AminoAcid userAltAA = null;
                            if (input.getAlt() != null) {
                                userAltAA = AminoAcid.fromOneOrThreeLetter(input.getAlt());

                                // variant non SNV
                                if (!AminoAcid.isSNV(gCoordRefAA, userAltAA) && !errorMap.containsKey(ERR_CODE_VARIANT_NON_SNV)) {
                                    errorMap.put(ERR_CODE_VARIANT_NON_SNV,
                                            new Message(Message.MessageType.WARN,
                                                    String.format(ERROR_MESSAGE.get(ERR_CODE_VARIANT_NON_SNV),
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

                if (!errorMap.isEmpty()) {
                    errorMap.keySet().forEach(k -> {
                        input.getMessages().add(errorMap.get(k));
                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty()) {
                    //if we haven't got a filtered set of genomic coordinates, return all possible combinations
                    if (!altGInputs.isEmpty()) {
                        input.getDerivedGenomicInputs().addAll(altGInputs);
                    }
                    else {
                        input.addError("Could not map protein input to genomic coordinate(s)");
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

                        String cdnaRef = input.getRef();
                        String cdnaAlt = input.getAlt();

                        if (gCoord.isReverseStrand()) {
                            cdnaRef = reverseDNA(cdnaRef);
                            cdnaAlt = reverseDNA(cdnaAlt);
                        }

                        if (!(gCoordRefAllele.equals(cdnaRef) &&
                                gCoordCodonPos.equals(input.getDerivedCodonPos()))) {
                            return;
                        }

                        GenomicInput gInput = new GenomicInput(input.getInputStr());
                        gInput.setChr(gCoordChr);
                        gInput.setPos(gCoordPos);
                        gInput.setRef(gCoordRefAllele);
                        gInput.setAlt(cdnaAlt);
                        input.getDerivedGenomicInputs().add(gInput);
                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty() && input.getMessages().isEmpty()) {
                    input.addError("Could not map cDNA input to genomic coordinate(s)");
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

    private Set<String> getAlternatesDNA(String ref) {
        return Arrays.asList("A", "T", "C", "G").stream().filter(s -> !s.equals(ref)).collect(Collectors.toSet());
    }

}
