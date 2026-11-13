package uk.ac.ebi.protvar.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Renaming a key orphans what is already stored under it; change these only with a cache.version bump.
class RedisKeysTest {

    @Test
    void storedKeysUnchanged() {
        assertEquals("v1:download:status:abc", RedisKeys.key("v1", RedisKeys.Download.STATUS + "abc"));
        assertEquals("v1:download:counts:", RedisKeys.key("v1", RedisKeys.Download.COUNTS));
        assertEquals("uniprotEntryByAccession", RedisKeys.Cache.UNIPROT_ENTRY_BY_ACCESSION);
        assertEquals("structuresByAccession", RedisKeys.Cache.STRUCTURES_BY_ACCESSION);
        assertEquals("inputs", RedisKeys.Cache.INPUTS);
    }
}
