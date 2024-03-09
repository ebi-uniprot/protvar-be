package uk.ac.ebi.protvar.utils;

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

}