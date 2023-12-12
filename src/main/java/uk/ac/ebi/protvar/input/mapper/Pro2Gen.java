package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.UniprotEntryCache;
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

@Service
@AllArgsConstructor
public class Pro2Gen {

    private ProtVarDataRepo protVarDataRepo;

    @Autowired
    UniprotEntryCache uniprotEntryCache;

    private UniprotRefseqRepo uniprotRefseqRepo;

    /*
e.g. Protein input
P22309 71 Gly Arg

Check 1
- can we go from Gly to Arg via SNP?

select accession, protein_position, chromosome, genomic_position, allele, codon, reverse_strand, codon_position
from genomic_protein_mapping
where (accession, protein_position) IN (('P22309', 71))

"accession"	"protein_position"	"chromosome"	"genomic_position"	"allele"	"codon"	"reverse_strand"	"codon_position"
"P22309"	"71"	"2"	"233760498"	"G"	"Gga"	"false"	"1"
"P22309"	"71"	"2"	"233760499"	"G"	"gGa"	"false"	"2"
"P22309"	"71"	"2"	"233760500"	"A"	"ggA"	"false"	"3"

Check 2
- does GGA encodes Gly(G)?

Determining the single gCoord from the 3 gCoords representing the aa (Gly/GGA)
i.e. determining the codon position (1,2, or 3)

For each mapping
"P22309"	"71"	"2"	"233760498"	"G"	"Gga"	"false"	"1"
codon position = 1
-> get possible SNVs (where G__ is fixed i.e. at position 1)
-> check if alt AA is one of them, if not, skip mapping
-> otherwise
    // determining alt allele from
    // - user input
    // refAA & altAA (e.g. Gly Arg)
    // - db mapping
    // ref allele (e.g. G)   -- NOTE: RNA allele (AUGC)
    // codon (e.g. Gga)      -- NOTE: DNA codon (ATGC)
    // codon position (e.g 1)

    -> possible SNVs at position 1 that encodes alt AA (Arg)
    -> for each possible SNV, diff with ref AA to find alt allele
    -> add each to genomic input list

 */
    public void convert(List<UserInput> proteinInputs) {

        // 1. get all the accessions and positions
        Set<Object[]> accPosSet = new HashSet<>();


        Set<String> rsAccs = proteinInputs.stream().filter(i -> i instanceof HGVSp)
                .map(i -> ((HGVSp) i).getRsAcc()).collect(Collectors.toSet());
        Map<String, List<String>> rsAccsMap = uniprotRefseqRepo.getRefSeqUniprotMap(rsAccs);

        for (UserInput input : proteinInputs) {

            if (input instanceof HGVSp) {
                HGVSp hgvsProt = ((HGVSp) input);
            List<String> uniprotAccs = rsAccsMap.get(hgvsProt.getRsAcc());
            if (uniprotAccs != null && uniprotAccs.size() > 0){
                List<String> head =  uniprotAccs.subList(0, 1);
                List<String> tail =  uniprotAccs.subList(1, uniprotAccs.size());

                hgvsProt.setAcc(head.get(0));
                accPosSet.add(new Object[]{head.get(0), hgvsProt.getPos()});

                if (tail != null && tail.size() > 1) {
                    hgvsProt.addWarning(String.format("RefSeq id mapped to multiple Uniprot accessions: %s. Providing mapping for first accession.", Arrays.toString(uniprotAccs.toArray())));
                }
                if (!uniprotEntryCache.isValidEntry(hgvsProt.getAcc())) {
                    hgvsProt.addWarning("Invalid mapped accession " + hgvsProt.getAcc());
                }
            } else {
                hgvsProt.addWarning("Could not map RefSeq ID to a Uniprot protein");
            }

            } else if(input instanceof ProteinInput) { // custom Protein
                ProteinInput customProt = ((ProteinInput) input);
                accPosSet.add(new Object[]{customProt.getAcc(), customProt.getPos()});

                if (!uniprotEntryCache.isValidEntry(customProt.getAcc())) {
                    customProt.addWarning("Invalid accession " + customProt.getAcc());
                }

            } else if (input instanceof HGVSc) {
                HGVSc cDNAProt = ((HGVSc) input);
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

            if (i instanceof HGVSp) {
                HGVSp input = (HGVSp) i;
                String acc = input.getAcc();
                Integer pos = input.getPos();
                //String refAa = input.getRef();
                //String altAa = input.getAlt();

                // optional pos/ref/alt aa
                // ACC POS
                // ACC POS AA - assumes ref if matches DB rec, otherwise alt

                String key = acc + "-" + pos;
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);
                // ^ all the genomic coordinates for protein accession and position

                Set<String> seen = new HashSet<>();

                // allele in dna - ATCG
                // codon in rna - AUCG
                // reverse strand
                // A<->T
                // C<->G
                // e.g. in g2p_mapping tbl
                // protein_position,protein_seq,allele,codon,codon_position,reverse_strand
                //                1,          M,     A,  Aug,             1,         false
                //                1,          M,     T,  aUg,             2,         false
                //                1,          M,     G,  auG,             3,         false
                //
                // protein_position,protein_seq,allele,codon,codon_position,reverse_strand
                //                1,          M,     T,  Aug,             1,          true
                //                1,          M,     A,  aUg,             2,          true
                //                1,          M,     C,  auG,             3,          true
                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        String gCoordChr = gCoord.getChromosome();
                        Integer gCoordPos = gCoord.getGenomeLocation();
                        String gCoordRefAllele = gCoord.getBaseNucleotide();

                        String curr = Commons.joinWithDash(gCoordChr, gCoordPos, gCoordRefAllele);
                        if (seen.contains(curr)) return;
                        seen.add(curr);

                        String gCoordRefAA = gCoord.getAa();
                        String gCoordCodon = gCoord.getCodon(); //should code for/translate into refAA
                        Integer gCoordCodonPos = gCoord.getCodonPosition();
                        Boolean gCoordIsReverse = gCoord.isReverseStrand();

                        Set<String> gCoordAltAlleles = getAlternates(gCoordRefAllele);

                        if (input.getRef() == null && input.getAlt() == null) {
                            for (String altAllele : gCoordAltAlleles) {
                                altAllele = gCoordIsReverse ? RNACodon.reverse(altAllele) : altAllele;
                                altAllele = altAllele.replace('U', 'T');
                                GenomicInput gInput = new GenomicInput(input.getInputStr());
                                gInput.setChr(gCoordChr);
                                gInput.setPos(gCoordPos);
                                gInput.setRef(gCoordRefAllele);
                                gInput.setAlt(altAllele);
                                input.getDerivedGenomicInputs().add(gInput);
                            }
                        } else if (input.getRef() != null && input.getAlt() != null) {

                            AminoAcid refAA = AminoAcid.fromOneOrThreeLetter(input.getRef());
                            AminoAcid gCoordRefAA_ = AminoAcid.fromOneOrThreeLetter(gCoordRefAA);

                            String codonUC = gCoordCodon.toUpperCase();

                            for (String altAllele : gCoordAltAlleles) {
                                String altCodon = codonUC.substring(0, gCoordCodonPos - 1) + altAllele +
                                        codonUC.substring(gCoordCodonPos);
                                RNACodon altRnaCodon = RNACodon.valueOf(altCodon);
                                AminoAcid altAA = altRnaCodon == null ? null : altRnaCodon.getAa();
                                AminoAcid userAltAA = AminoAcid.fromOneOrThreeLetter(input.getAlt());
                                if (altAA != null && userAltAA != null && altAA == userAltAA) {
                                    altAllele = gCoordIsReverse ? RNACodon.reverse(altAllele) : altAllele;
                                    altAllele = altAllele.replace('U', 'T');
                                    GenomicInput gInput = new GenomicInput(input.getInputStr());
                                    gInput.setChr(gCoordChr);
                                    gInput.setPos(gCoordPos);
                                    gInput.setRef(gCoordRefAllele);
                                    gInput.setAlt(altAllele);
                                    input.getDerivedGenomicInputs().add(gInput);

                                    // Checks-
                                    // 1. refAa and db ref should be the same
                                    if (!refAA.equals(gCoordRefAA_)) {
                                        input.addWarning(String.format("Reference AA mismatch. Should be %s, not %s", gCoordRefAA, input.getRef()));
                                        input.setRef(gCoordRefAA);
                                    }

                                    // 2. ref/alt should be via SNV

                                    if (!AminoAcid.isSNV(refAA, altAA)) {
                                        input.addInfo(String.format("%s to %s is a non-SNV. ", refAA.name(), userAltAA.name()));
                                    }
                                }
                            }
                        }

                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty() && input.getMessages().isEmpty()) {
                    input.addError("Could not map HGVSp. input to genomic coordinate(s).");
                }

            } else if(i instanceof ProteinInput) {
                ProteinInput input = (ProteinInput) i;

                AminoAcid refAA = AminoAcid.fromOneLetter(input.getRef());
                AminoAcid altAA = AminoAcid.fromOneLetter(input.getAlt());

                if (!AminoAcid.isSNV(refAA, altAA)) {
                    input.addInfo(String.format("%s to %s is a non-SNV. ", refAA.name(), altAA.name()));
                }

                // ref -> alt change is only possible by a SNV change at these
                // positions e.g. {1}, {2}, {1,2}, etc
                final Set<Integer> codonPositions = refAA.changedPositions(altAA);
                //if (codonPositions == null || codonPositions.isEmpty()) // means ref->alt AA not possible via SNV?
                //	codonPositions = new HashSet<>(Arrays.asList(1, 2, 3));

                String key = input.getAcc() + "-" + input.getPos();
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);
                Set<String> seen = new HashSet<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        String gCoordChr = gCoord.getChromosome();
                        Integer gCoordPos = gCoord.getGenomeLocation();
                        String gCoordRefAllele = gCoord.getBaseNucleotide();
                        //String gCoordAcc = gCoord.getAccession();
                        //Integer gCoordProteinPos = gCoord.getIsoformPosition();
                        String gCoordRefAA = gCoord.getAa();
                        String gCoordCodon = gCoord.getCodon(); //should code for/translate into refAA
                        Integer gCoordCodonPos = gCoord.getCodonPosition();
                        Boolean gCoordIsReverse = gCoord.isReverseStrand();

                        String curr = gCoordChr + "-" + gCoordPos + "-" + gCoordRefAllele;
                        if (seen.contains(curr)) return;
                        seen.add(curr);

                        if (codonPositions != null && !codonPositions.isEmpty()
                                && !codonPositions.contains(gCoordCodonPos)) return;

                        RNACodon refRNACodon = RNACodon.valueOf(gCoordCodon.toUpperCase());

                        Set<RNACodon> altRNACodons_ = refRNACodon.getSNVs().stream()
                                .filter(c -> c.getAa().equals(altAA))
                                .collect(Collectors.toSet());

                        if (altRNACodons_.isEmpty()) {
                            return;
                        }

                        char charAtCodonPos = refRNACodon.name().charAt(gCoordCodonPos - 1); // = refAllele?
                        List<RNACodon> altRNACodons = altRNACodons_.stream()
                                .filter(c -> c.name().charAt(gCoordCodonPos - 1) != charAtCodonPos)
                                .collect(Collectors.toList());

                        if (altRNACodons.isEmpty()) {
                            return;
                        }

                        Set<String> altAlleles = new HashSet<>();
                        for (RNACodon altRNACodon : altRNACodons) {
                            altAlleles.add(snvDiff(refRNACodon, altRNACodon));
                        }

                        for (String altAllele : altAlleles) {
                            altAllele = gCoordIsReverse ? RNACodon.reverse(altAllele) : altAllele;
                            altAllele = altAllele.replace('U', 'T');
                            GenomicInput gInput = new GenomicInput(input.getInputStr());
                            gInput.setChr(gCoordChr);
                            gInput.setPos(gCoordPos);
                            gInput.setRef(gCoordRefAllele);
                            gInput.setAlt(altAllele);
                            input.getDerivedGenomicInputs().add(gInput);
                            if (!refAA.getOneLetter().equalsIgnoreCase(gCoordRefAA))
                                input.getMessages().add(new Message(Message.MessageType.WARN, "User reference and mapping record AA mismatch (" + refAA.name() + " vs. " + refRNACodon.name() + ")"));
                        }
                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty() && input.getMessages().isEmpty()) {
                    input.addError("Could not map Protein input to genomic coordinate(s).");
                }
            } else if (i instanceof HGVSc) {
                HGVSc input = (HGVSc) i;
                String acc = input.getDerivedUniprotAcc();
                Integer pos = input.getDerivedProtPos();

                String key = acc + "-" + pos;
                List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);

                Set<String> seen = new HashSet<>();

                if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
                    gCoordsForProtein.forEach(gCoord -> {
                        String gCoordChr = gCoord.getChromosome();
                        Integer gCoordPos = gCoord.getGenomeLocation();
                        String gCoordRefAllele = gCoord.getBaseNucleotide();
                        Integer gCoordCodonPos = gCoord.getCodonPosition();

                        if (!(gCoordRefAllele.equals(input.getRef()) &&
                                gCoordCodonPos.equals(input.getDerivedCodonPos()))) {
                            return;
                        }

                        String curr = Commons.joinWithDash(gCoordChr, gCoordPos, gCoordRefAllele);
                        if (seen.contains(curr)) return;
                        seen.add(curr);

                        GenomicInput gInput = new GenomicInput(input.getInputStr());
                        gInput.setChr(gCoordChr);
                        gInput.setPos(gCoordPos);
                        gInput.setRef(gCoordRefAllele);
                        gInput.setAlt(input.getAlt());
                        input.getDerivedGenomicInputs().add(gInput);
                    });
                }

                if (input.getDerivedGenomicInputs().isEmpty() && input.getMessages().isEmpty()) {
                    input.addError("Could not map cDNA input to genomic coordinate(s).");
                }
            }
        });

    }

    private Set<String> getAlternates(String ref) {
        return RNACodon.RNA_LETTERS.stream().filter(s -> !s.equals(ref)).collect(Collectors.toSet());
    }

    private String snvDiff(RNACodon c1, RNACodon c2) {
        for (int p=0; p<3; p++) {
            if (c1.name().charAt(p) != c2.name().charAt(p))
                return String.valueOf(c2.name().charAt(p));
        }
        return null;
    }

}
