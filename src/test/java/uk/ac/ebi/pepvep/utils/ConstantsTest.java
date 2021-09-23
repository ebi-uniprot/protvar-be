package uk.ac.ebi.pepvep.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConstantsTest {
  @Test
  void comma() {
    Assertions.assertEquals(",", Constants.COMMA);
  }

  @Test
  void na() {
    Assertions.assertEquals("N/A", Constants.NA);
  }

  @Test
  void invalidChro() {
    Assertions.assertEquals("Invalid chromosome ", Constants.NOTE_INVALID_INPUT_CHROMOSOME);
  }

  @Test
  void invalidPos() {
    Assertions.assertEquals("Position should be a number ", Constants.NOTE_INVALID_INPUT_POSITION);
  }

  @Test
  void invalidRef() {
    Assertions.assertEquals("Invalid alternative ", Constants.NOTE_INVALID_INPUT_REF);
  }

  @Test
  void invalidAlt() {
    Assertions.assertEquals("Invalid alternative ", Constants.NOTE_INVALID_INPUT_ALT);
  }
}