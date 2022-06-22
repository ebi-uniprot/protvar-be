package uk.ac.ebi.protvar.utils;

import lombok.Getter;
import uk.ac.ebi.protvar.exception.UnexpectedUseCaseException;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum AminoAcid {
    ALA("A", "Ala", "Alanine"),
    ARG("R", "Arg", "Arginine"),
    ASN("N", "Asn", "Asparagine"),
    ASP("D", "Asp", "Aspartic acid"),
    CYS("C", "Cys", "Cysteine"),
    GLN("Q", "Gln", "Glutamine"),
    GLU("E", "Glu", "Glutamic acid"),
    GLY("G", "Gly", "Glycine"),
    HIS("H", "His", "Histidine"),
    ILE("I", "Ile", "Isoleucine"),
    LEU("L", "Leu", "Leucine"),
    LYS("K", "Lys", "Lysine"),
    MET("M", "Met", "Methionine"),
    PHE("F", "Phe", "Phenylalanine"),
    PRO("P", "Pro", "Proline"),
    SER("S", "Ser", "Serine"),
    THR("T", "Thr", "Threonine"),
    TRP("W", "Trp", "Tryptophan"),
    TYR("Y", "Tyr", "Tyrosine"),
    VAL("V", "Val", "Valine"),
    TER("*", "Ter", "Termination (stop codon)"),

    /* extended/non-standard AAs - from old AminoAcidsThreeLetter enum */
    ASX("B", "Asx", "Asparagine or aspartate", true),
    GLX("Z", "Glx", "Glutamine or glutamate", true),
    SEC("U", "Sec", "Selenocysteine", true),
    UNK("X", "Unk", "Any / unknown", true),

    /* extended AAs - not included before */
    PYL("O", "Pyl", "Pyrrolysine", true),
    XLE("J", "Xle", "Leucine or isoleucine", true)
    ;

    public String formatted() {
        return threeLetters + " (" + oneLetter +")";
    }
    private String oneLetter;
    private String threeLetters;
    private String name;
    private boolean extended;

    private List<RNACodon> rnaCodons = new ArrayList<>();
    private Map<AminoAcid, Set<Integer>> altAACodonPositions = new HashMap<>();

    public final static Set<Character> VALID_AMINO_ACID_CHARS = new HashSet<>();
    public final static String VALID_LETTERS;

    static {
        Arrays.stream(AminoAcid.standardValues()).forEach(
                aminoAcid -> {
                    VALID_AMINO_ACID_CHARS.add(aminoAcid.getOneLetter().charAt(0));
                }
        );
        VALID_LETTERS = AminoAcid.VALID_AMINO_ACID_CHARS.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    static {
        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    Arrays.stream(RNACodon.values()).forEach(rnaCodon -> {
                        if (rnaCodon.getAa() != null && rnaCodon.getAa().equals(aminoAcid))
                            aminoAcid.rnaCodons.add(rnaCodon);
                    });
                });

        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    aminoAcid.rnaCodons.stream().forEach(rnaCodon -> {
                        for (Map.Entry<Integer, List<RNACodon>> entry : rnaCodon.getPossibleVariants().entrySet()) {
                            Integer pos = entry.getKey();
                            List<RNACodon> variantCodons = entry.getValue();
                            for (RNACodon variantCodon : variantCodons) {
                                AminoAcid aa = variantCodon.getAa();
                                if (aminoAcid.altAACodonPositions.containsKey(aa)) {
                                    aminoAcid.altAACodonPositions.get(aa).add(pos);
                                } else {
                                    Set posSet = new HashSet<Integer>();
                                    posSet.add(pos);
                                    aminoAcid.altAACodonPositions.put(aa, posSet);
                                }
                            }
                        }
                    });
                });
    }

    AminoAcid(String oneLetter, String threeLetters, String name) {
        this.oneLetter = oneLetter;
        this.threeLetters = threeLetters;
        this.name = name;
    }

    AminoAcid(String oneLetter, String threeLetters, String name, Boolean extended) {
        this(oneLetter, threeLetters, name);
        this.extended = extended;
    }

    public static AminoAcid[] standardValues() {
        return Arrays.stream(AminoAcid.values()).filter(aa -> !aa.extended).toArray(AminoAcid[]::new);
    }

    public static AminoAcid[] nonStandardValues() {
        return Arrays.stream(AminoAcid.values()).filter(aa -> aa.extended).toArray(AminoAcid[]::new);
    }

    public static void main(String[] args) {

        System.out.println(AminoAcid.SER.getChangePos(AminoAcid.ARG));
        System.out.println(AminoAcid.SER.changedPositions(AminoAcid.ARG));

        for (AminoAcid aa : AminoAcid.standardValues()) {
            System.out.println(aa + ", encoded by -> " + aa.getRnaCodons() + ", altAACodonPosMap -> " + aa.getAltAACodonPositions());
        }

        for (RNACodon codon : RNACodon.values()) {
            System.out.println(codon +
                    ", aa -> " + codon.getAa() +
                    ", SNVs -> " + codon.getSNVs() +
                    " Variant AAs -> " + codon.getVariantAAs());
        }

        /*
        System.out.println(RNACodon.GGG.getSNVs(0));
        System.out.println(RNACodon.GGG.getSNVs());
        System.out.println(RNACodon.GGG.getVariantAAs(0));
        System.out.println(RNACodon.GGG.getVariantAAs());
        System.out.println(AminoAcid.ALA +" "+ AminoAcid.ALA.rnaCodons);
        */
    }

    public static AminoAcid fromOneLetter(String oneLetter) {
        for (AminoAcid aa : AminoAcid.values()) {
            if (aa.getOneLetter().equals(oneLetter))
                return aa;
        }
        //throw new UnexpectedUseCaseException(oneLetter + " is invalid one letter amino acid");
        return AminoAcid.UNK;
    }

    public Set<Integer> getChangePos(AminoAcid alt) {
        // Get all RNA codons that encode ref (this) amino acid
        // For each RNA codon that encodes the ref (this) amino acid,
        // get AA at each possible position
        Set<Integer> codonChangePositions = new HashSet<>();
        for (RNACodon codon : this.getRnaCodons()) {
            Map<Integer, List<RNACodon>> variantMap = codon.getPossibleVariants();
            for (Integer pos : variantMap.keySet()) {
                List<RNACodon> variantCodons = variantMap.get(pos);
                if (variantCodons.stream().filter(variantCodon -> variantCodon.getAa().equals(alt)).count() > 0)
                    codonChangePositions.add(pos);
            }
        }
        return codonChangePositions;
    }

    public Set<Integer> changedPositions(AminoAcid alt) {
        return this.getAltAACodonPositions().get(alt);
    }

}