-- Takes a DNA base (A/T/G/C)
-- Applies reverse complement logic if reverse_strand = true
-- Converts DNA to RNA (T → U)
CREATE OR REPLACE FUNCTION rna_base_for_strand(dna_base CHAR, reverse_strand BOOLEAN)
RETURNS CHAR AS $$
BEGIN
  IF reverse_strand THEN
    RETURN replace(translate(dna_base, 'ATGC', 'TACG'), 'T', 'U');
ELSE
    RETURN replace(dna_base, 'T', 'U');
END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;