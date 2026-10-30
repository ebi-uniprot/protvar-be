package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.UniprotAccessionCache;
import uk.ac.ebi.protvar.cache.ProteinCache;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.record.AccessionPosition;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.types.Codon;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.*;
import java.util.stream.Collectors;
import static uk.ac.ebi.protvar.input.MessageCode.*;

@Service
@AllArgsConstructor
public class Pro2Gen {

    private static final Logger LOGGER = LoggerFactory.getLogger(Pro2Gen.class);

    private MappingRepo mappingRepo;

    @Autowired
    UniprotAccessionCache uniprotAccessionCache;

    @Autowired
    ProteinCache proteinCache;

    public void convert(Map<VariantType, List<VariantInput>> groupedInputs, TreeMap<String, List<String>> refseqIdMap) {
        List<VariantInput> proteinInputs = new ArrayList<>();
        if (groupedInputs.get(VariantType.PROTEIN) != null)
            proteinInputs.addAll(groupedInputs.get(VariantType.PROTEIN));
        if (groupedInputs.get(VariantType.CODING_DNA) != null)
            proteinInputs.addAll(groupedInputs.get(VariantType.CODING_DNA));
        if (!proteinInputs.isEmpty())
            convert(proteinInputs, refseqIdMap);
    }

    private void convert(List<VariantInput> proteinInputs, TreeMap<String, List<String>> refseqIdMap) {
        // 1. get all the accessions and positions (deduplicated by AccessionPosition)
        Set<AccessionPosition> uniqueAccPos = new HashSet<>();
        for (VariantInput input : proteinInputs) {

            if (input.getFormat() == VariantFormat.HGVS_PROTEIN) {
                ProteinInput hgvsProt = (ProteinInput) input;

                List<String> uniprotAccs = Coding2Pro.getUniprotAccs(hgvsProt.getRefseqId(), refseqIdMap, hgvsProt);
                if (uniprotAccs != null && uniprotAccs.size() > 0){
                    List<String> head =  uniprotAccs.subList(0, 1);

                    hgvsProt.setAccession(head.get(0));
                    uniqueAccPos.add(new AccessionPosition(head.get(0), hgvsProt.getPosition()));

                    if (uniprotAccs.size() > 1) {
                        hgvsProt.addMessage(MessageCode.PV351_REFSEQ_SEVERAL_ACCS,
                                String.join(", ", uniprotAccs), head.get(0));
                    }
                    if (!proteinCache.isMapped(hgvsProt.getAccession())) {
                        if (uniprotAccessionCache.isKnown(hgvsProt.getAccession()))
                            hgvsProt.addMessage(MessageCode.PV110_ACC_NOT_MAPPED, hgvsProt.getAccession());
                        else
                            hgvsProt.addMessage(MessageCode.PV353_REFSEQ_ACC_NOT_IN_UNIPROT,
                                    hgvsProt.getRefseqId(), hgvsProt.getAccession());
                    }
                } else {
                    hgvsProt.addMessage(MessageCode.PV352_REFSEQ_NO_PROTEIN);
                }

            } else if(input instanceof ProteinInput) { // internal Protein
                ProteinInput internalProt = (ProteinInput) input;
                uniqueAccPos.add(new AccessionPosition(internalProt.getAccession(), internalProt.getPosition()));

                if (!proteinCache.isMapped(internalProt.getAccession())) {
                    MessageCode error = uniprotAccessionCache.isKnown(internalProt.getAccession())
                            ? MessageCode.PV110_ACC_NOT_MAPPED : MessageCode.PV111_ACC_NOT_HUMAN;
                    internalProt.addMessage(error, internalProt.getAccession());
                }

            } else if (input.getFormat() == VariantFormat.HGVS_CODING) {
                HGVSCodingInput codingInput = (HGVSCodingInput) input;
                if (codingInput.getDerivedUniprotAcc() != null) {
                    if (!proteinCache.isMapped(codingInput.getDerivedUniprotAcc())) {
                        if (uniprotAccessionCache.isKnown(codingInput.getDerivedUniprotAcc()))
                            codingInput.addMessage(MessageCode.PV110_ACC_NOT_MAPPED, codingInput.getDerivedUniprotAcc());
                        else
                            codingInput.addMessage(MessageCode.PV353_REFSEQ_ACC_NOT_IN_UNIPROT,
                                    codingInput.getRefseqId(), codingInput.getDerivedUniprotAcc());
                    }
                    if (codingInput.getDerivedProtPos() != null)
                        uniqueAccPos.add(new AccessionPosition(codingInput.getDerivedUniprotAcc(), codingInput.getDerivedProtPos()));
                }
            }
        }

        // 2. get all the relevant db records by accessions and positions
        List<Object[]> accPosList = uniqueAccPos.stream()
                .map(ap -> new Object[]{ap.accession(), ap.position()})
                .toList();
        Map<String, List<GenomeToProteinMapping>> gCoords = mappingRepo.getMappingsByAccPos(accPosList)
                .stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getVariantKeyProtein));

        // 3. we expect each protein input to have 3 genomic coordinates (in normal cases),
        //	which we will try to pin down to one, if possible, based on the user inputs
        proteinInputs.stream().filter(i -> i.isValid()).forEach(i -> {

            if (i.getType() == VariantType.PROTEIN) { // INTERNAL_PROT or HGVS_PROT formats
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
                List<MessageCode> errors = new ArrayList<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        // Guard against incomplete g2p_mapping rows. The DB columns
                        // base_nucleotide / aa / codon can be null for some accessions
                        // (data-pipeline issue tracked separately) and an unguarded
                        // toUpperCase below would NPE the whole download. codonPosition
                        // is a primitive int — SQL NULL becomes 0 silently, no NPE.
                        if (gCoord.getBaseNucleotide() == null || gCoord.getAa() == null) {
                            LOGGER.warn("Skipping incomplete g2p mapping for {} pos {}: ref={}, aa={}",
                                    input.getAccession(), input.getPosition(), gCoord.getBaseNucleotide(), gCoord.getAa());
                            return;
                        }
                        if (gCoord.getCodon() == null) {
                            // no readable codon: the change can't be matched, so keep the position, every alt (PV-113)
                            String noCodonKey = Commons.joinWithDash(gCoord.getChromosome(), gCoord.getGenomeLocation(),
                                    gCoord.getBaseNucleotide());
                            if (!seen.add(noCodonKey)) return;
                            if (!errors.contains(PV113_NO_CODON)) {
                                errors.add(PV113_NO_CODON);
                                input.addMessage(PV113_NO_CODON);
                            }
                            for (String alt : GenomicInput.getAlternateBases(gCoord.getBaseNucleotide()))
                                input.getDerivedGenomicVariants().add(new GenomicVariant(gCoord.getChromosome(),
                                        gCoord.getGenomeLocation(), gCoord.getBaseNucleotide(), alt));
                            return;
                        }

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
                            if (!errors.contains(PV102_REF_AA_MISSING)) {
                                errors.add(PV102_REF_AA_MISSING);
                                input.addMessage(PV102_REF_AA_MISSING, input.getPosition(), gCoordRefAA.getThreeLetter());
                            }

                            if (!errors.contains(PV104_ALT_AA_MISSING)) {
                                errors.add(PV104_ALT_AA_MISSING);
                                input.addMessage(PV104_ALT_AA_MISSING);
                            }

                            for (String altAllele : possibleAltAlleles) {
                                //altAllele = gCoordIsReverse ? reverseDNA(altAllele) : altAllele;
                                GenomicVariant genomicVariant = new GenomicVariant(gCoordChr, gCoordPos, gCoordRefAllele, altAllele);
                                input.getDerivedGenomicVariants().add(genomicVariant);
                            }
                        } else if (input.getRefAA() != null) { // ref provided

                            AminoAcid refAA = AminoAcid.fromOneOrThreeLetter(input.getRefAA());

                            // reference mismatch
                            if (!refAA.equals(gCoordRefAA) && !errors.contains(PV101_REF_AA_MISMATCH)) {
                                errors.add(PV101_REF_AA_MISMATCH);
                                input.addMessage(PV101_REF_AA_MISMATCH,
                                        refAA.getThreeLetter(), gCoordRefAA.getThreeLetter(), input.getPosition(), gCoordRefAA.getThreeLetter());
                                input.setRefAA(gCoordAa);
                            }

                            AminoAcid userAltAA = null;
                            if (input.getAltAA() == null) { // alt not provided, or "?" (unknown)
                                MessageCode varMsg = input.isAltUnknown() ? PV105_ALT_AA_UNKNOWN : PV104_ALT_AA_MISSING;
                                if (!errors.contains(varMsg)) {
                                    errors.add(varMsg);
                                    input.addMessage(varMsg);
                                }
                            }
                            else {
                                userAltAA = AminoAcid.fromOneOrThreeLetter(input.getAltAA());

                                // variant non SNV
                                if (!AminoAcid.isSNV(gCoordRefAA, userAltAA) && !errors.contains(PV103_ALT_AA_NOT_SNV)) {
                                    errors.add(PV103_ALT_AA_NOT_SNV);
                                    input.addMessage(PV103_ALT_AA_NOT_SNV,
                                            userAltAA.getThreeLetter(), gCoordRefAA.getThreeLetter());
                                }
                            }

                            for (String altAllele : possibleAltAlleles) {

                                // alt codon + consequence AA via the shared Codon helper (was inline + altAlleleIfReverse)
                                AminoAcid altAA = Codon.altAA(gCoordCodon, gCoordCodonPos, altAllele, gCoordIsReverse);

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
                        input.addMessage(PV112_POS_NOT_MAPPED);
                    }
                    else {
                        input.getDerivedGenomicVariants().addAll(altGInputs);
                    }
                }

            } else if (i.getFormat() == VariantFormat.HGVS_CODING) {
                HGVSCodingInput codingInput = (HGVSCodingInput) i;

                String key = VariantKey.protein(codingInput.getDerivedUniprotAcc(), codingInput.getDerivedProtPos());
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);

                Set<String> seen = new HashSet<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        if (gCoord.getBaseNucleotide() == null) {
                            LOGGER.warn("Skipping incomplete g2p mapping for HGVS coding {} pos {}: null baseNucleotide",
                                    codingInput.getDerivedUniprotAcc(), codingInput.getDerivedProtPos());
                            return;
                        }

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
                    codingInput.addMessage(PV337_C_POS_NOT_MAPPED);
                }
            }
        });
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
