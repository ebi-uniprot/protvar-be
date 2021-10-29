package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.exception.UnexpectedUseCaseException;

public enum AminoAcidsThreeLetter {

  ALA("A", "ALA"), ARG("R", "ARG"), ASN("N", "ASN"),
  ASP("D", "ASP"), ASX("B", "ASX"), CYS("C", "CYS"),
  GLU("E", "GLU"), GLN("Q", "GLN"), GLX("Z", "GLX"),
  GLY("G", "GLY"), HIS("H", "HIS"), ILE("I", "ILE"),
  LEU("L", "LEU"), LYS("K", "LYS"), MET("M", "MET"),
  PHE("F", "PHE"), PRO("P", "PRO"), SER("S", "SER"),
  THR("T", "THR"), TRP("W", "TRP"), TYR("Y", "TYR"),
  VAL("V", "VAL"), SEC("U", "SEC"), UNK("X", "UNK"),
  TER("*", "*");

  AminoAcidsThreeLetter(String oneLetter, String threeLetter) {
    this.oneLetter = oneLetter;
    this.threeLetter = threeLetter;
  }

  private final String oneLetter;
  private final String threeLetter;

  public static String getThreeLetterFromSingleLetter(String oneLetter) {
    for (AminoAcidsThreeLetter type : AminoAcidsThreeLetter.values()) {
      if (type.oneLetter.equals(oneLetter))
        return type.threeLetter;
    }
    throw new UnexpectedUseCaseException(oneLetter + " is not handled in AminoAcidsThreeLetter");
  }
}
