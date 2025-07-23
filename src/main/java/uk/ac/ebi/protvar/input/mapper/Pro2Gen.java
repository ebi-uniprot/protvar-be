package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.UniprotEntryCache;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.types.Codon;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.*;
import java.util.stream.Collectors;
import static uk.ac.ebi.protvar.input.ErrorConstants.*;

@Service
@AllArgsConstructor
public class Pro2Gen {

    private MappingRepo mappingRepo;

    @Autowired
    UniprotEntryCache uniprotEntryCache;

    public void convert(Map<Type, List<UserInput>> groupedInputs, TreeMap<String, List<String>> refseqIdMap) {
        List<UserInput> proteinInputs = new ArrayList<>();
        if (groupedInputs.get(Type.PROTEIN) != null)
            proteinInputs.addAll(groupedInputs.get(Type.PROTEIN));
        if (groupedInputs.get(Type.CODING) != null)
            proteinInputs.addAll(groupedInputs.get(Type.CODING));
        if (!proteinInputs.isEmpty())
            convert(proteinInputs, refseqIdMap);
    }

    private void convert(List<UserInput> proteinInputs, TreeMap<String, List<String>> refseqIdMap) {
        // 1. get all the accessions and positions
        List<Object[]> accPosList = new ArrayList<>();
        for (UserInput input : proteinInputs) {

            if (input.getFormat() == Format.HGVS_PROT) {
                ProteinInput hgvsProt = (ProteinInput) input;

                List<String> uniprotAccs = Coding2Pro.getUniprotAccs(hgvsProt.getRefseqId(), refseqIdMap, hgvsProt);
                if (uniprotAccs != null && uniprotAccs.size() > 0){
                    List<String> head =  uniprotAccs.subList(0, 1);
                    List<String> tail =  uniprotAccs.subList(1, uniprotAccs.size());

                    hgvsProt.setAccession(head.get(0));
                    accPosList.add(new Object[]{head.get(0), hgvsProt.getPosition()});

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
                    if (!uniprotEntryCache.isValidEntry(hgvsProt.getAccession())) {
                        hgvsProt.addWarning(
                                String.format(ErrorConstants.HGVS_UNIPROT_ACC_NOT_FOUND.getErrorMessage(),
                                        hgvsProt.getRefseqId(), hgvsProt.getAccession()));
                    }
                } else {
                    hgvsProt.addWarning(ErrorConstants.HGVS_REFSEQ_NO_PROTEIN);
                }

            } else if(input instanceof ProteinInput) { // internal Protein
                ProteinInput internalProt = (ProteinInput) input;
                accPosList.add(new Object[]{internalProt.getAccession(), internalProt.getPosition()});

                if (!uniprotEntryCache.isValidEntry(internalProt.getAccession())) {
                    internalProt.addError(String.format(ErrorConstants.PROT_UNIPROT_ACC_NOT_FOUND.toString(), internalProt.getAccession()));
                }

            } else if (input.getFormat() == Format.HGVS_CODING) {
                HGVSCodingInput codingInput = (HGVSCodingInput) input;
                if (codingInput.getDerivedUniprotAcc() != null) {/*
                    codingInput.addInfo(String.format(
                        ErrorConstants.HGVS_REFSEQ_MAPPED_TO_PROTEIN.getErrorMessage(),
                            codingInput.getDerivedUniprotAcc()));*/

                    if (!uniprotEntryCache.isValidEntry(codingInput.getDerivedUniprotAcc()))
                        codingInput.addWarning(
                                String.format(ErrorConstants.HGVS_UNIPROT_ACC_NOT_FOUND.getErrorMessage()
                                        , codingInput.getRefseqId(), codingInput.getDerivedUniprotAcc()));
                    if (codingInput.getDerivedProtPos() != null)
                        accPosList.add(new Object[]{codingInput.getDerivedUniprotAcc(), codingInput.getDerivedProtPos()});
                }
            }
        }

        // 2. get all the relevant db records by accessions and positions
        Map<String, List<GenomeToProteinMapping>> gCoords = mappingRepo.getMappingsByAccPos(accPosList)
                .stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getVariantKeyProtein));

        // 3. we expect each protein input to have 3 genomic coordinates (in normal cases),
        //	which we will try to pin down to one, if possible, based on the user inputs
        proteinInputs.stream().filter(i -> i.isValid()).forEach(i -> {

            if (i.getType() == Type.PROTEIN) { // INTERNAL_PROT or HGVS_PROT formats
                ProteinInput input = (ProteinInput) i;

                String key = VariantKey.protein(input.getAccession(), input.getPosition());
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

                List<GenomicVariant> altGInputs = new ArrayList<>();
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

                        Set<String> possibleAltAlleles = GenomicInput.getAlternateBases(gCoordRefAllele); // allele is in DNA letters -ATCG

                        AminoAcid gCoordRefAA = AminoAcid.fromOneOrThreeLetter(gCoordAa);

                        // both ref and alt not provided
                        if (input.getRefAA() == null && input.getAltAA() == null) {
                            // Ref & var empty
                            if (!errors.contains(ERR_CODE_REF_EMPTY)) {
                                errors.add(ERR_CODE_REF_EMPTY);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        String.format(ERR_CODE_REF_EMPTY.getErrorMessage(), input.getPosition(), gCoordRefAA.getThreeLetter())));
                            }

                            if (!errors.contains(ERR_CODE_VAR_EMPTY)) {
                                errors.add(ERR_CODE_VAR_EMPTY);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        ERR_CODE_VAR_EMPTY.getErrorMessage()));
                            }

                            for (String altAllele : possibleAltAlleles) {
                                //altAllele = gCoordIsReverse ? reverseDNA(altAllele) : altAllele;
                                GenomicVariant genomicVariant = new GenomicVariant(gCoordChr, gCoordPos, gCoordRefAllele, altAllele);
                                input.getDerivedGenomicVariants().add(genomicVariant);
                            }
                        } else if (input.getRefAA() != null) { // ref provided

                            AminoAcid refAA = AminoAcid.fromOneOrThreeLetter(input.getRefAA());

                            // reference mismatch
                            if (!refAA.equals(gCoordRefAA) && !errors.contains(ERR_CODE_REF_MISMATCH)) {
                                errors.add(ERR_CODE_REF_MISMATCH);
                                input.getMessages().add(new Message(Message.MessageType.WARN,
                                        String.format(ERR_CODE_REF_MISMATCH.getErrorMessage(),
                                                refAA.getThreeLetter(), gCoordRefAA.getThreeLetter(), input.getPosition(), gCoordRefAA.getThreeLetter())));
                                input.setRefAA(gCoordAa);
                            }

                            AminoAcid userAltAA = null;
                            if (input.getAltAA() == null) { // alt not provided
                                if (!errors.contains(ERR_CODE_VAR_EMPTY)) {
                                    errors.add(ERR_CODE_VAR_EMPTY);
                                    input.getMessages().add(new Message(Message.MessageType.WARN,
                                            ERR_CODE_VAR_EMPTY.getErrorMessage()));
                                }
                            }
                            else {
                                userAltAA = AminoAcid.fromOneOrThreeLetter(input.getAltAA());

                                // variant non SNV
                                if (!AminoAcid.isSNV(gCoordRefAA, userAltAA) && !errors.contains(ERR_CODE_VARIANT_NON_SNV)) {
                                    errors.add(ERR_CODE_VARIANT_NON_SNV);
                                    input.getMessages().add(new Message(Message.MessageType.WARN,
                                            String.format(ERR_CODE_VARIANT_NON_SNV.getErrorMessage(),
                                                    userAltAA.getThreeLetter(), gCoordRefAA.getThreeLetter())));
                                }
                            }

                            String codonUC = gCoordCodon.toUpperCase();

                            for (String altAllele : possibleAltAlleles) {

                                String altCodon = codonUC.substring(0, gCoordCodonPos - 1) +
                                        altAlleleIfReverse(altAllele, gCoordIsReverse) +
                                        codonUC.substring(gCoordCodonPos);

                                Codon altRnaCodon = Codon.valueOf(altCodon);
                                AminoAcid altAA = altRnaCodon.getAa();

                                GenomicVariant genomicVariant = new GenomicVariant(gCoordChr, gCoordPos, gCoordRefAllele, altAllele);

                                // filter out codon that doesn't code for user variant amino acid
                                if (userAltAA != null && !altAA.equals(userAltAA)) {
                                    altGInputs.add(genomicVariant);
                                    continue; // check the next altAllele
                                }
                                input.getDerivedGenomicVariants().add(genomicVariant);
                            }
                        }

                    });
                }

                if (input.getDerivedGenomicVariants().isEmpty()) {
                    //if we haven't got a filtered set of genomic coordinates, return all possible combinations
                    if (altGInputs.isEmpty()) {
                        input.addError(PROT_NO_GEN_MAPPING);
                    }
                    else {
                        input.getDerivedGenomicVariants().addAll(altGInputs);
                    }
                }

            } else if (i.getFormat() == Format.HGVS_CODING) {
                HGVSCodingInput codingInput = (HGVSCodingInput) i;

                String key = VariantKey.protein(codingInput.getDerivedUniprotAcc(), codingInput.getDerivedProtPos());
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
                        if (!gCoordCodonPos.equals(codingInput.getDerivedCodonPos()))
                            return;

                        String cdnaRef = codingInput.getRefBase();
                        String cdnaAlt = codingInput.getAltBase();

                        if (gCoord.isReverseStrand()) {
                            cdnaRef = reverseDNA(cdnaRef);
                            cdnaAlt = reverseDNA(cdnaAlt);
                        }

                        if (!gCoordRefAllele.equals(cdnaRef))
                            return;

                        GenomicVariant genomicVariant = new GenomicVariant(gCoordChr, gCoordPos, gCoordRefAllele, cdnaAlt);
                        codingInput.getDerivedGenomicVariants().add(genomicVariant);
                    });
                }

                if (codingInput.getDerivedGenomicVariants().isEmpty()) {
                    codingInput.addError(CDNA_NO_GEN_MAPPING);
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
