package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.RNACodon;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class Pro2Gen {

    private ProtVarDataRepo protVarDataRepo;

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
        proteinInputs.stream().map(i -> (ProteinInput) i).forEach(input -> {
            accPosSet.add(new Object[]{input.getAcc(), input.getPos()});
        });

        // 2. get all the relevant db records by accessions and positions
        Map<String, List<GenomeToProteinMapping>> gCoords = protVarDataRepo.getMappingsByAccPos(accPosSet)
                .stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupByProteinAccAndPos));

        // 3. we expect each protein input to have 3 genomic coordinates (in normal cases),
        //	which we will try to pin down to one, if possible, based on the user inputs
        proteinInputs.stream().map(i -> (ProteinInput) i).forEach(input -> {


            AminoAcid refAA = AminoAcid.fromOneLetter(input.getRef());
            AminoAcid altAA = AminoAcid.fromOneLetter(input.getAlt());

            Set<AminoAcid> possibleAltAAs = new HashSet();
            for (RNACodon refCodon : refAA.getRnaCodons()) {
                possibleAltAAs.addAll(refCodon.getAltAAs());
            }

            if (!possibleAltAAs.contains(altAA)) {
                input.addInfo(String.format("%s to %s is a non-SNV. ", refAA.name(), altAA.name()));
            }

            // ref -> alt change is only possible by a SNV change at these
            // positions e.g. {1}, {2}, {1,2}, etc
            final Set<Integer> codonPositions = refAA.changedPositions(altAA);
            //if (codonPositions == null || codonPositions.isEmpty()) // means ref->alt AA not possible via SNV?
            //	codonPositions = new HashSet<>(Arrays.asList(1, 2, 3));

            String key = input.getAcc() +"-"+ input.getPos();
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

                    char charAtCodonPos = refRNACodon.name().charAt(gCoordCodonPos-1); // = refAllele?
                    List<RNACodon> altRNACodons = altRNACodons_.stream()
                            .filter(c -> c.name().charAt(gCoordCodonPos-1) != charAtCodonPos)
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
                            input.getMessages().add(new Message(Message.MessageType.WARN, "User reference and mapping record AA mismatch ("+refAA.name()+" vs. "+refRNACodon.name()+")"));
                    }
                });
            }

            if (input.getDerivedGenomicInputs().isEmpty()) {
                input.addError("Could not map Protein input to genomic coordinate(s).");
            }
        });

    }

    private String snvDiff(RNACodon c1, RNACodon c2) {
        for (int p=0; p<3; p++) {
            if (c1.name().charAt(p) != c2.name().charAt(p))
                return String.valueOf(c2.name().charAt(p));
        }
        return null;
    }

}
