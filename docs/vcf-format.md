# VCF format

* PepVEP currently supports the annotation of single nucleotide variants (SNVs).
* PepVEP accepts user variant data using the standard VCF format with eight columns. Details can be found at https://www.ebi.ac.uk/training/online/courses/human-genetic-variation-introduction/variant-identification-and-analysis/understanding-vcf-format/
* PepVEP is also able to support VCF-like formats and only the first five columns are used. Any white space delimiters are tolerated.
* **Header lines** in the input file are optional and will be ignored. But they should start with #

## Input fields
The minimum number of fields required is three (when REF/ALT) or four. Will will only take chromosome, coordinate, reference allele and alternative allele to process output. We will ignore other fields e-g id, quality, filter status, additional information etc

### Chromosome

The first field will always be interpreted as the chromosome. Permitted chromosomes are 1-22, X, Y, Mitochondria (chrM, mitochondria, mitochondrion, MT, mtDNA, mit) all fields are case insensitive. Any other characters will return a chromosome error and will not be interpreted.

### Coordinate

The second field will always be interpreted as the genomic chromosome position. It must only contain numeric characters. Any other characters will return a coordinate error.

### Variant ID

The third field is by default and by VCF convention, the variant ID, for example an RS ID but is optional. This ID will be displayed in the results but not interpreted.

The identity of the fourth (and fifth) fields can alter the interpretation of this field and the ID can be omitted eg: (see below examples for more details)



**IDs are not permitted to contain only a nucleotide base eg "C" or "A" or "G" or "T" or any combination eg "C/T", "A/G". Otherwise PepVEP will interpret these as reference or variant alleles.**

### Reference allele

The column following the ID is interpreted as the reference allele. If no ID is supplied by the user then the third column is considered as the reference allele. The reference allele is currently limited to "A" or "C" or "G" or "T". Currently PepVEP only supports SNV annotation and so multiple nucleobases indicating deletions or structural variations are not permitted.

### Alternative allele

The column following the reference allele is interpreted as the alternative allele. If no ID is supplied by the user then the fourth column is considered as the alternative allele. The alternative allele is currently limited to "A" or "C" or "G" or "T". Currently PepVEP only supports SNV annotation and so multiple nucleobases indicating insertions or structural variations are not permitted.

### Additional fields

Additional fields can be included in addition to the three/four mandatory fields but they are not interpreted by PepVEP.

### Examples

In following examples chromosome will be 12, position will be 1345789 and C→T SNV
* 12 1345789 C/T
* 12 1345789 123 C/T - with ID as 123
* 12 1345789 C T
* 12 1345789 . C T - with ID as . (dot)
* 12 1345789 rc123 C T - with ID as rc123

### Watch out
* 12 1345789 C T G - Here 'C' will consider as reference allele NOT ID
* 12 1345789 C/T A G - Here 'C' will consider as reference allele and 'T' will consider as alternative allele and 'A' & 'G' will be ignored

### Other examples
* 20 14370 rs6054257 G A 29 PASS NS=3;DP=14;AF=0.5;DB;H2 GT:GQ:DP:HQ 0|0:48:1:51,51 1|0:48:8:51,51 1/1:43:5:.,.
  * 20 is chromosome
  * 14370 will be position
  * rs6054257 is an ID
  * G -> A is SNV
  * we ignore the final three fields, quality, filter status, additional information
* 1       935847  862561  C       G       .       .       ALLELEID=824441;CLNDISDB=MedGen:CN517202;CLNDN=not_provided;CLNHGVS=NC_000001.11:g.935847C>G;CLNREVSTAT=criteria_provided,_single_submitter;CLNSIG=Uncertain_significance;CLNVC=single_nucleotide_variant;CLNVCSO=SO:0001483;GENEINFO=SAMD11:148398;MC=SO:0001583|missense_variant;ORIGIN=1
  * 1 is chromosome
  * 935847 will be position
  * 862561 is an ID
  * C -> G is SNV



