CREATE TABLE codon_table (
 codon TEXT PRIMARY KEY,
 amino_acid TEXT
);
-- add all codon->amino acid mappings
INSERT INTO codon_table (codon, amino_acid) VALUES
('AAA', 'K'), ('AAC', 'N'), ('AAG', 'K'), ('AAU', 'N'),
('ACA', 'T'), ('ACC', 'T'), ('ACG', 'T'), ('ACU', 'T'),
('AGA', 'R'), ('AGC', 'S'), ('AGG', 'R'), ('AGU', 'S'),
('AUA', 'I'), ('AUC', 'I'), ('AUG', 'M'), ('AUU', 'I'),
('CAA', 'Q'), ('CAC', 'H'), ('CAG', 'Q'), ('CAU', 'H'),
('CCA', 'P'), ('CCC', 'P'), ('CCG', 'P'), ('CCU', 'P'),
('CGA', 'R'), ('CGC', 'R'), ('CGG', 'R'), ('CGU', 'R'),
('CUA', 'L'), ('CUC', 'L'), ('CUG', 'L'), ('CUU', 'L'),
('GAA', 'E'), ('GAC', 'D'), ('GAG', 'E'), ('GAU', 'D'),
('GCA', 'A'), ('GCC', 'A'), ('GCG', 'A'), ('GCU', 'A'),
('GGA', 'G'), ('GGC', 'G'), ('GGG', 'G'), ('GGU', 'G'),
('GUA', 'V'), ('GUC', 'V'), ('GUG', 'V'), ('GUU', 'V'),
('UAA', '*'), ('UAC', 'Y'), ('UAG', '*'), ('UAU', 'Y'),
('UCA', 'S'), ('UCC', 'S'), ('UCG', 'S'), ('UCU', 'S'),
('UGA', '*'), ('UGC', 'C'), ('UGG', 'W'), ('UGU', 'C'),
('UUA', 'L'), ('UUC', 'F'), ('UUG', 'L'), ('UUU', 'F');

CREATE TABLE amino_acid (
 one_letter CHAR(1) PRIMARY KEY,
 three_letter VARCHAR(3),
 full_name VARCHAR(20)
);
-- amino acid info
INSERT INTO amino_acid (one_letter, three_letter, full_name) VALUES
('A', 'Ala', 'Alanine'),
('C', 'Cys', 'Cysteine'),
('D', 'Asp', 'Aspartic Acid'),
('E', 'Glu', 'Glutamic Acid'),
('F', 'Phe', 'Phenylalanine'),
('G', 'Gly', 'Glycine'),
('H', 'His', 'Histidine'),
('I', 'Ile', 'Isoleucine'),
('K', 'Lys', 'Lysine'),
('L', 'Leu', 'Leucine'),
('M', 'Met', 'Methionine'),
('N', 'Asn', 'Asparagine'),
('P', 'Pro', 'Proline'),
('Q', 'Gln', 'Glutamine'),
('R', 'Arg', 'Arginine'),
('S', 'Ser', 'Serine'),
('T', 'Thr', 'Threonine'),
('V', 'Val', 'Valine'),
('W', 'Trp', 'Tryptophan'),
('Y', 'Tyr', 'Tyrosine'),
('*', 'Ter', 'Stop codon');