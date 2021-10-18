# CSV format Version 1.0
The output file will be in CSV (comma separated values) format. The file is divided into the following general categories:

* Header lines
* Variant data
   * User input
   * Mapping notes
   * Genomic mapping
   * Protein mapping
   * Functional annotations
   * Population annotations
   * Structural annotations
## Syntax
The output file has 36 columns in total. Every column value is double quoted, for example “User input” and then separated by a comma.
Columns can contain “N/A” as a value indicating either:
* The user did not requested the data (for example if only mappings are requested without annotation)
* We can not provide a value for reasons such as:
   * No data exists in our database in the category for this variant
   * One of the external APIs is not working
## Header lines
Metadata about the file and data. Theses lines include:
* File version, for example - version-1
* information about columns or columns. Values are all capital letters and words are separated by spaces

The following descriptions are of the columns in the download file. They are numbered from left to right. The letters in brackets correspond to the columns when visualised in a spreadsheet.

## User input
These are the columns created from the user variant input. There are six columns:
* User input
* Chromosome
* Coordinate
* ID 
* Reference_allele 
* Alternative_allele

### 1(A). User_input
This field replicates the user input with no changes to the format. Users can use this field to match their input data to the annotated output file.
### 2(B). Chromosome
Only numbers 1-22 or “X” or “Y” or mitochondria (chrM, mitochondria, mitochondrion, MT, mtDNA, mit) are accepted. All case insensitive.
### 3(C). Coordinate
The genomic coordinate position of the variant as interpreted from the user input. Only numeric characters. 
### 4(D). ID
This is a field which can optionally be provided by the user to keep track of their variants or store information about teh variant which will be retained in the output file.
### 5(E). Reference_allele 
This is the reference allele. It is defined by the nucleotide identity at that coordinate in the reference genome build. If the user inputted nucleotide differs from the reference build the reference build nucleotide identity will be shown and not the user inputted identity. This conflict will be noted in the "notes" column. Any user inputs except 'A', 'G', 'C', 'T' will be flagged in the "notes section.
### 6(F). Alternative_allele
This is the alternative allele and will always match the user input. Any user inputs except 'A', 'G', 'C', 'T' will be flagged in the "notes section.

## Mapping notes
* 7(G). Mapping_notes

### 7(G). Mapping_notes
Single column which describes potential issue with the user input. It will contain “N/A” if there is nothing to report. Possible issues include:
* Invalid input - Such as a nonesense chromosome, a non-numeric coordinate of invalid nucleotides in either the reference or variant allele positions.
* The input sequence does not match the reference. Possible reasons for this include
   * user error
   * an updated sequence in the reference build 
   * because the user has submitted variants from an older reference genome such as GRCh37)
* Mapping not found. Reasons may include:
   * variant in an intergenic region
   * variant in an intronic region
   * no transcript maps to the canonical isoform

## Genomic mapping
Contains information regarding mapping of the user variant input to the relevant gene(s), transcript(s) and codon. The category contains 6 columns:
* Gene
* Codon_change
* Strand
* CADD_phred_like_score
* Canonical_isoform_transcripts
* MANE_transcript

### 8(H). Gene
The gene symbol as defined by the HGNC (https://www.genenames.org/about/guidelines/)
Symbols contain only uppercase Latin letters and Arabic numerals, and punctuation is avoided, with an exception for 
hyphens in specific groups.
### 9(I). Codon_change
The format is three nucleotides containing the reference allele which make the codon, followed by “/” and then the 
three corresponding nucleotides but containing the alternative nucleotide. The position which is changed is capitalised, 
for example aCg/aTg where the middle nucleotide of the codon is changed from a Cytosine (C) to a Thymine (T).
### 10(J). Strand
The reference genome and variants are stated as the positive strand only, therefore if a user enteres G->T variant but the gene is on the negative strand the codon change displayed will be C->A (the reverse complement).
### 11(K). CADD_phred_like_score
The CADD (Combined Annotation Dependent Depletion) score is devised by the University of Washington - https://cadd.gs.washington.edu/ . 
They calculate a score for every possible change in the genome. The phred-like score  ("scaled C-scores") ranges from 1 to 99. It is based on the rank of each variant relative to all possible 8.6 billion substitutions in the human reference genome.
### 12(L). Canonical_isoform_transcripts
The transcripts and transcript translation identifiers which correspond to the UniProt canonical isoform. Transcripts 
(DNA sequences) have an ID starting with “ENST”. There can be several different transcripts which encode the same 
isoform because they may differ in their untranslated (non-coding) regions at either end. The translated transcript has an ID 
starting “ENSP”. For example [ENSP00000337353(ENST00000335725,ENST00000123456).
### 13(M). MANE_transcript
MANE (Matched Annotation between NCBI and EBI). - One of the transcripts is selected as the representative by NCBI and Ensembl. This transcript
may not translate into the UniProt canonical isoform sequence. If the MANE select corresponds directly to the isoform described in the row the MANE select ID is given. If they do not match "N/A" is found in the column and the MANE select ID is found with the corresponding transcript in the "Alternative_isoform_mappings" column. 

## Protein mapping
Contains information regarding mapping of the user variant input to the encoded protein(s). The category contains 6 columns:
* Uniprot_canonical_isoform_(non_canonical)
* Alternative_isoform_mappings
* Protein_name
* Amino_acid_position
* Amino_acid_change
* Consequences

### 14(N). Uniprot_canonical_isoform_(non_canonical)
This is the accession of the canonical isoform of the protein if PepVEP can map to this. PepVEP always attempts to map to this isoform because 
most of the UniProt annotations are based on numbering in the canonical. Sometimes PepVEP cannot map to the canonical isoform
but can to another isoform (sequence version of the protein). In these cases brackets are displayed around the accession to show that the mapping is to a non-canonical isoform.
accessions? P12345
### 15(O). Alternative_isoform_mappings
Details about each isoform including the isoform accession, amino acid position in the isoform, amino acid change, 
consequence and ENSP and ENST identifiers. Many genes have several transcripts caused by alternative splicing, 
some of which translate into different isoforms. Here we list details about all the isoforms where we can map from 
genomic location to isoform. Isoforms are separated by "|".
### 16(P). Protein_name
The full protein name from UniProt.
### 17(Q). Amino_acid_position
The position of the amino acid in the UniProt canonical isoform or the alternative isoform shown in the Uniprot_canonical_isoform_(non_canonical) column.
### 18(R). Amino_acid_change
The identity of the reference and alternative amino acid caused by the variant three letter amino acid nomenclature separated by "/". Stop codons are shown as asterisk (*).
### 19(S). Consequences
The consequence of the variant on the amino acid/protein.

## Functional outputs
These columns contain functional annotations regarding the variant amino acid, region and protein. This data is retrieved from UniProt API. There 
are 11 columns in this category:
* Residue_function_(evidence)
* Region_function_(evidence)
* Protein_existence_evidence
* Protein_length
* Entry_last_updated
* Sequence_last_updated
* Protein_catalytic_activity
* Protein_complex
* Protein_sub_cellular_location
* Protein_family
* Protein_interactions_PROTEIN(gene)

### 20(T). Residue_function_(evidence)
Functional features specifically describing the residue encoded by the user submitted variant. 
### 21(U). Region_function_(evidence)
This column describes functional features of the region which the residue encoded by the user submitted variant falls. The range of the region is provided after the ";". Overlapping regions describing the variant are separated by "|".
but any of them could be.
### 22(V). Protein_existence_evidence
Describes if there is experimental evidence to support the existence of the protein
### 23(W). Protein_length
The length of the UniProt canonical isoform sequence.
### 24(X). Entry_last_updated
When the UniProt entry was last updated with any type of information.
### 25(Y). Sequence_last_updated
When the canonical isoform sequence was last updated.
### 26(Z). Protein_catalytic_activity
Describes the reactions previously ascribed to this protein. These are not necessarily reactions affected by the variant amino
acid but they could be. The RHEA ID (a SIB reactions database) is given as is the evidence(s) from publications. Different reactions are separated by "|". For example: 
RHEA:25017(PubMed:[16824732,9593664,9811831])|RHEA:20629(PubMed:[9256433])
### 27(AA). Protein_complex
Describes whether the protein containing the varant exists in a complex. 
### 28(AB). Protein_sub_cellular_location
Describes the location within the cell where the protein is localised. There may be more than one location if multiple have been described.
### 29(AC). Protein_family
Describes the functional family that the protein belongs to.
### 30(AD). Protein_interactions_PROTEIN(gene)
This shows which other proteins have been shown to interact with the variant containing protein. This data is from the EMBL-EBI IntAct 
database and is predominantly from manual curation. The format is: UniProt accession(gene symbol). Different interacting partners are separated by ";".

## Population outputs
There are 11 columns in this category:
* Genomic_location
* Cytogenetic_band
* Other_identifiers_for_the_variant
* Diseases_associated_with_variant
* Variants_colocated_at_residue_position

### 31(AE). Genomic_location
The variant described in HGVS format. This is a different way of describing the variant which included the sequence 
version of the reference.
### 32(AF). Cytogenetic_band
The region of the chromosome containing the variant position. Cytogenic bands are areas of chromosomes rich in 
actively transcribing DNA.
### 33(AG). Other_identifiers_for_the_variant
Description of the same variant (position and nucleotide change) as the user entered in different databases. 
The source database name is given, separated by the variant ID with "-" then separated from the clinical consequence with ";". Each separate databse is separated by "|". For example, ClinVar-RCV000003593;Pathogenic|UniProt-VAR_017144;Pathogenic.
### 34(AH). Diseases_associated_with_variant
Describes diseases from literature which have been associated with the specific variant entered by the user. There may be multiple diseases 
listed which are separated by “|”. The evidence for each disease is in brackets which may be a CliVar ID or Pubmed link to a publication.
### 35(AI). Variants_colocated_at_residue_position
This column describes other variants which have been described at the same AMINO ACID position. As a codon is three 
nucleotides this means that the variants here could be at any one of three positions and can be any alternative allele.

## Structural outputs
This category has one column: 
### 36(AJ). Position_in_structures
The column shows which PDB protein structures contain the variant. This is not an exhaustive list of all structures
of the protein as some structures will not cover the region containing the variant. 
The format is: PDB_accesion;chain_position_in_structure,chain_position_in_structure;structure_resolution;structure_method. Structures are separated by "|".
