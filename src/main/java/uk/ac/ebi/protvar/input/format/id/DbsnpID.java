package uk.ac.ebi.protvar.input.format.id;

import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;

public class DbsnpID extends IDInput {
    public DbsnpID(String inputStr) {
        super(inputStr);
        setFormat(Format.DBSNP);
        setId(inputStr.toLowerCase()); // for db case-insensitive query to work
        // db table which stores RS IDs with lower-case prefix
        // ensures Rs, rS, RS work
    }
}
