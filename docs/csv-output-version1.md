# CSV format Version 1
The output file will be CSV (comma separated values). This file can be divided into seven major categories:
1. Header lines
2. Input
3. Notes
4. Mapping outputs
5. Functional outputs
6. Population outputs
7. Structural outputs
## Syntax
File has 36 columns in total. Every Column value is double quoteded e-g “user input” and then separated by comma.
Columns can contain “N/A” as value indicating two things
1. User didn’t requested this value
2. We can not provide value for any reason
   1. We don’t have information about it
   2. External API don’t have information about it
   3. External API is not working
## Header lines
Think about header lines as metadata about the file and data you are downloading. Below will be the lines
1. File version e-g version-1
2. Second line will be information about columns or columns names
   1. Values will be all capital letters
   2. Words separated by space
## Input
These are the combination of columns which we have created from user input. Following are the columns
1. USER INPUT
2. CHROMOSOME
3. COORDINATE
4. ID
5. REF
6. ALT

### 1. User input
This field replicates the user input with no changes to the format. (it is useful for comparing with the retrieved 
mappings when there is a sequence conflict and also for the user to map their inputs to the outputs).
### 2. Chromosome
Can only be numbers 1..22 or “X” or “Y” or mitochondria (chrM, mitochondria, mitochondrion, MT, mtDNA, 
mit [case insensitive])
### 3. Coordinate
Must be numeric only. (Numbering is independent for each chromosome. It starts at 1 but has different ends according 
to length. Generally 1 is the longest and they decrease in size. Mitochondria is the smallest (although not technically 
a chromosome) Y is very small also and shares much of the sequence with X which is quite large. Both X and Y are 
sometimes referred to as chromosome 23)
### 4. Id
This is provided by the user to keep track of their variants. Sometimes the user may add an “rs” ID here which is a 
DB SNP reference (a repository of single nucleotide polymorphisms maintained by the NCBI).
### 5. Ref
This is the reference allele. It is defined by the nucleotide identity at that coordinate in the reference genome build.
(roughly this equates to the most common nucleotide in that position across many sequenced humans).
### 6. Alt
Alternative allele. When a sequence from an individual shows a nucleotide which is different from the reference it is 
called a variant allele. These are very common and usually not associated with disease. However, when these occur in 
coding regions and cause a change in amino acid they are more likely to cause problems. Especially if that amino acid 
is highly conserved because its specific side chain plays an important role for the function or structure of the protein.

## Notes
In this category we have only one column “NOTES”. 

### 7. Notes
It explains if there is something special about user input. It can contain “N/A” as a value if there is no note present.
Examples
* Invalid input - (we should be explicit about which part is invalid)
* The input sequence does not match the reference - (this can happen due to user error, an updated sequence in the 
reference build or because the user has submitted variants from an older reference genome such as GRCh37)
* Mapping not found - (we should try to be as explicit as possible here.perhaps not for this version. Eventually 
it should state something like “intergenic region” “intronic region”, “no transcript maps to the canonical isoform” etc.

## Mapping outputs
These columns contain the information about mapping of user input. We have 12 columns in this category
1. GENE
2. CODON CHANGE
3. STRAND
4. CADD PHRED
5. TRANSCRIPTS OF THE CANONICAL ISOFORM
6. MANE TRANSCRIPT
7. UNIPROT CANONICAL ISOFORM
8. ALTERNATIVE ISOFORM DETAILS
9. PROTEIN NAME
10. AMINO ACID POSITION
11. AMINO ACID CHANGE
12. CONSEQUENCES

### 8. Gene
The gene symbol as defined by the HGNC (https://www.genenames.org/about/guidelines/)
Symbols contain only uppercase Latin letters and Arabic numerals, and punctuation is avoided, with an exception for 
hyphens in specific groups.
### 9. Codon Change
The format is three nucleotides containing the reference allele which make the codon, followed by “/” and then the 
three corresponding nucleotides but containing the alternative nucleotide. The position which is changed is capitalised 
eg aCg/aTg where the middle nucleotide of the codon is changed from a C to a T.
### 10. Strand
Genomic DNA normally exists as a double helix. The nucleotides opposite each other on the other strand are complementary
eg A is opposite T and G is opposite C. One strand is called the forward strand and the other is the reverse. They are 
both read 5’ to 3’ so they are read in opposite directions. Approximately half of the genes are encoded on the positive 
strand and half on the reverse. However, the reference genome and variants are stated as the positive strand only. 
This means that if a user enteres G->T variant but the gene is on the reverse strand the codon change will be C->A 
( the reverse complement).
### 11. Cadd Phred
The CADD (Combined Annotation Dependent Depletion) score is devised by the University of 
Washington - https://cadd.gs.washington.edu/ . 
They calculate a score for every possible change in the genome so even if we can’t map we should be able to retrieve 
this (hover in the current version we only store.
### 12. Transcripts of the canonical isoform
The transcripts and transcript translation identifiers which correspond to the canonical isoform. Transcripts 
(DNA sequences) have an ID starting with “ENST”. There can be several different transcripts which encode the same 
isoform because they may differ in their untranslated regions at either end. The translated transcript has an ID 
starting “ENSP”. It is the result of using the codon table to translate the transcript sequence (3 nucleotides at a time)
into an amino acid sequence. Eg [ENSP00000337353(ENST00000335725)
### 13. MANE Transcript:Canonical isoform match
(MANE = Matched Annotation between NCBI and EBI). - One of the transcripts is selected as the representative but this 
may not translate into the UniProt canonical isoform sequence.In this new column we could add “yes” and the RefSeq 
identifier if the MANE select transcript .translates to the UniProt canonical isoform and “no” if the MANE select 
transcript does not translate to the UniProt canonical isoform.
### 14. Uniprot Canonical isoform (non-canonical)
This is the accession of the canonical isoform of the protein. PepVEP always attempts to map to this isoform because 
most of the UniProt annotations are based on numbering in the canonical. Sometimes we cannot map to the canonical 
but we can to another isoform (sequence version of the protein). It would be great if we could distinguish between 
instances where we can map to the canonical and when we cannot. Can we add an asterisk or brackets around non-canonical
accessions? P12345
### 15. Alternative Isoform details
Details about each isoform including the isoform accession, amino acid position in the isoform, amino acid change, 
consequence and ENSP and ENST identifiers. Many genes have several transcripts caused by alternative splicing, 
some of which translate into different isoforms. Here we list details about all the isoforms where we can map from 
genomic location to isoform.
### 16. Protein Name
The full protein name from UniProt (this can potentially be quite long and contain alpha and numeric characters as 
well as special characters such as “-” and “/”
### 17. Amino Acid Position
The position of the amino acid in the UniProt canonical isoform
### 18. Amino Acid Change
The identity of the amino acid change in three letter amino acid nomenclature. Stop codons are shown as asterisk (*).
### 19. Consequences
The consequence of the SNV on the amino acid. Currently this is missense/synonymous (where there is no change to the 
amino acid)/ stop gain

## Functional outputs
These columns contain functional information/annotations on user input which is retrieved from uniprot API. There 
will be 11 columns in this category
1. RESIDUE FUNCTION (EVIDENCE)
2. REGION FUNCTION (EVIDENCE)
3. PROTEIN EXISTENCE
4. PROTEIN LENGTH
5. ENTRY LAST UPDATED
6. SEQUENCE LAST UPDATED
7. PROTEIN CATALYTIC ACTIVITY
8. PROTEIN COMPLEX
9. PROTEIN SUBCELLULAR LOCATION
10. PROTEIN FAMILY
11. PROTEIN INTERACTIONS - PROTEIN(GENE)

### 20. Residue Function (Evidence)
This column describes functional features of the residue encoded by the user submitted variant. (It is from the proteins
API and is any feature where the start and end point are the same eg start:213, end:213. These kind of features are 
specific to a single amino acid.
### 21. Region Function (Evidence)
This column describes functional features of the region which the residue encoded by the user submitted variant is in.
(It is from the proteins API and is any feature where the start point is lower than the variant position and the end 
point is higher eg start:200, end:213 where the variant is position 206. An example may be a binding region where 
several amino acids interact with another protein. In this case it is unclear which are the more critical residues 
but any of them could be.
### 22. Protein Existence
Describes if there is experimental evidence to support the existence of the protein
### 23. Protein Length
The length of the UniProt canonical isoform sequence
### 24. Entry Last Updated
When the UniProt entry was last updated with any type of information
### 25. Sequence Last Updated
When the canonical isoform sequence was last updated (this happens very rarely as the canonical isoforms are very stable).
### 26. Protein Catalytic Activity
Describes the reactions annotated for this protein. These are not necessarily reactions affected by the variant amino
acid but they could be. The RHEA ID (a SIB reactions database) is given as is the evidence from publications.
### 27. Protein Complex
Describes whether the complex exists in a complex. Proteins often complex either with other copies of themselves 
(eg homodimer) or with other proteins eg (heterodimers).
### 28. Protein Subcellular Location
Describes the location within the cell where the protein is localised. This might for example be the cytoplasm, 
nucleus or endoplasmic reticulum. There may be more than one location if multiple have been described.
### 29. Protein Family
Describes the functional family that the protein belongs to. Proteins are not equally different to each other. 
They can be clustered into similar structures/functions which often are evolved from a common ancestor. This helps 
the user to understand the type of protein without knowing this particular instance of it.
### 30. Protein Interactions - Protein(Gene)
This shows which other proteins have been shown to interact with the variant protein. This data is from the IntACt 
database and is predominantly from manual curation. The format is: UniProt accession(gene symbol) ; UniProt 
accession(gene symbol) ; ...

## Population outputs
This category will have below five columns
1. GENOMIC LOCATION
2. CYTOGENETIC BAND
3. OTHER IDENTIFIERS FOR SAME VARIANT
4. DISEASES ASSOCIATED WITH VARIANT
5. VARIANTS CO-LOCATED AT SAME RESIDUE POSITION

### 31. Genomic Location
The variant described in HGVS format. This is a different way of describing the variant which included the sequence 
version of the reference. This is currently only working for previously described variants.
### 32. Cytogenetic Band
The region of the chromosome containing the variant position. Cytogenic bands are areas of chromosomes rich in 
actively transcribing DNA.
### 33. Other Identifiers For Same Variant
Description of the same variant as the user entered in different databases. The position and nucleotide change may 
have been described previously. The format is: Database-variant_ID;allele_frequency/clinical_consequence | ...
### 34. Diseases Associated With Variant
Describes diseases from literature which have been associated with the variant. There may be multiple diseases 
listed which are separated by “|”. The evidence for each disease is in parentheses which may be a CliVar ID or 
Pubmed idea to a publication.
### 35. Variants Co-located At Same Residue Position
This column describes other variants which have been described at the same AMINO ACID position. As a codon is three 
nucleotides this means that the variants here could be at any one of three positions and can be any alternative allele.
## Structural outputs
This category will have only one column "STRUCTURE".
### 36. Structure
Column will contain data from the PDB (protein data bank) api. Contain contains “N/A” as value (see above syntax for reasons).
The column shows which PDB protein structures contain the variant. This is not an exhaustive list of all structures
of teh protein as some structures will not cover the required region. 
The format is: PDB_accesion;chain_position_in_structure,chain_position_in_structure;structure_resolution;structure_method
| next structure... TODO explain above column and what values this can have
