package uk.ac.ebi.protvar.repo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.types.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingVariantRepoTest {

    private final CodingVariantRepo repo = new CodingVariantRepo(null);
    {
        ReflectionTestUtils.setField(repo, "mappingTable", "map");
        ReflectionTestUtils.setField(repo, "uniprotRefseqTable", "refseq");
        ReflectionTestUtils.setField(repo, "structureTable", "str");
    }

    private List<String> filters(MappingRequest r, MapSqlParameterSource p) {
        return repo.filters(r, p);
    }

    @Test
    void noFilters() {
        assertTrue(filters(new MappingRequest(), new MapSqlParameterSource()).isEmpty());
    }

    @Test
    void flagFiltersAreColumns() {
        MappingRequest r = new MappingRequest();
        r.setKnown(true); r.setPocket(true); r.setInteract(true); r.setExperimentalModel(true);
        r.setDiseaseAssociation(true); r.setPtm(true); r.setMutagen(true); r.setDomain(true);
        r.setBinding(true); r.setActsite(true); r.setTransmem(true);
        assertEquals(List.of("known", "exp_structure", "pocket", "interface", "disease", "ptm", "mutagen", "domain",
                "binding", "actsite", "transmem"), filters(r, new MapSqlParameterSource()));
    }

    @Test
    void falseFlagsIgnored() {
        MappingRequest r = new MappingRequest();
        r.setKnown(false); r.setPocket(false);
        assertTrue(filters(r, new MapSqlParameterSource()).isEmpty());
    }

    @Test
    void caddBandsAreOrOfHalfOpenRanges() {
        MappingRequest r = new MappingRequest();
        r.setCadd(List.of(CaddCategory.PROBABLY_DELETERIOUS, CaddCategory.HIGHLY_LIKELY_DELETERIOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("((cadd >= CAST(:caddMin0 AS real) AND cadd < CAST(:caddMax0 AS real)) OR (cadd >= CAST(:caddMin1 AS real) AND cadd < CAST(:caddMax1 AS real)))"),
                filters(r, p));
        assertEquals(25.0, p.getValue("caddMin0"));
        assertEquals(30.0, p.getValue("caddMax0"));
        assertEquals(30.0, p.getValue("caddMin1"));
    }

    @Test
    void popeveOpenEndedBands() {
        MappingRequest r = new MappingRequest();
        r.setPopeve(List.of(PopEveClass.SEVERE, PopEveClass.UNLIKELY_DELETERIOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("((popeve < CAST(:popeveMax0 AS real)) OR (popeve >= CAST(:popeveMin1 AS real)))"), filters(r, p));
        assertEquals(-5.056, p.getValue("popeveMax0"));
        assertEquals(-4.617, p.getValue("popeveMin1"));
    }

    @Test
    void amClassesAsArray() {
        MappingRequest r = new MappingRequest();
        r.setAm(List.of(AmClass.PATHOGENIC, AmClass.AMBIGUOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("am_class = ANY(:amClasses)"), filters(r, p));
        assertArrayEquals(new Integer[]{1, -1}, (Integer[]) p.getValue("amClasses"));
    }

    @Test
    void rangesAndAlleleFreq() {
        MappingRequest r = new MappingRequest();
        r.setAviMin(20.0); r.setEsm1bMin(-12.0); r.setEsm1bMax(-3.0);
        r.setConservationMin(0.8);
        r.setAlleleFreq(List.of(AlleleFreqCategory.RARE));
        assertEquals(List.of("avi >= CAST(:aviMin AS real)", "esm1b >= CAST(:esm1bMin AS real)", "esm1b <= CAST(:esm1bMax AS real)",
                "conservation >= CAST(:conservationMin AS real)", "((af >= CAST(:afMin0 AS real) AND af < CAST(:afMax0 AS real)))"),
                filters(r, new MapSqlParameterSource()));
    }

    @Test
    void stabilityUsesMaxForLikelyMinForUnlikely() {
        MappingRequest r = new MappingRequest();
        r.setStability(List.of(StabilityChange.LIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_max >= 2"), filters(r, new MapSqlParameterSource()));
        r.setStability(List.of(StabilityChange.UNLIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_min < 2"), filters(r, new MapSqlParameterSource()));
        r.setStability(List.of(StabilityChange.LIKELY_DESTABILISING, StabilityChange.UNLIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_max IS NOT NULL"), filters(r, new MapSqlParameterSource()));
    }

    @Test
    void idPositionsOneSelectPerIdentifierUnioned() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = repo.idPositions(List.of(new Identifier(IdentifierType.UNIPROT, "P22304"),
                new Identifier(IdentifierType.GENE, "BRCA2")), p);
        assertEquals("""
                SELECT DISTINCT m.chromosome AS id_chr, m.genomic_position AS id_pos FROM map m WHERE m.accession = :id0
                UNION SELECT DISTINCT m.chromosome AS id_chr, m.genomic_position AS id_pos FROM map m WHERE m.gene_name = :id1""",
                sql);
        assertEquals("P22304", p.getValue("id0"));
        assertEquals("BRCA2", p.getValue("id1"));
    }

    @Test
    void idPositionsEnsemblVersionAndRefseq() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = repo.idPositions(List.of(new Identifier(IdentifierType.ENSEMBL, "enst00000380152.8"),
                new Identifier(IdentifierType.ENSEMBL, "ENSG00000139618.2"),
                new Identifier(IdentifierType.REFSEQ, "NP_000050")), p);
        assertTrue(sql.contains("WHERE m.enst = :id0 AND m.enstv = :id0v"));
        assertEquals("ENST00000380152", p.getValue("id0"));
        assertEquals("8", p.getValue("id0v"));
        assertTrue(sql.contains("WHERE m.ensg = :id1\n"), "ENSG matches the base id only");
        assertTrue(sql.contains("FROM refseq WHERE refseq_acc ILIKE :id2 || '.%'"));
    }

    @Test
    void idPositionsPdbJoinsObservedRegions() {
        String sql = repo.idPositions(List.of(new Identifier(IdentifierType.PDB, "1ABC")), new MapSqlParameterSource());
        assertTrue(sql.contains("JOIN (SELECT DISTINCT accession, observed_regions FROM str WHERE pdb_id = LOWER(:id0)) s"));
        assertTrue(sql.contains("m.accession = s.accession AND"));
        assertTrue(sql.contains("observed_regions"));
    }

    @Test
    void idPositionsUnmatchableIdentifierAddsNothing() {
        String sql = repo.idPositions(List.of(new Identifier(IdentifierType.ENSEMBL, "ENSX123")), new MapSqlParameterSource());
        assertTrue(sql.endsWith("WHERE false"));
    }

    @Test
    void servesIdsWhenFilteredSortedOrMulti() {
        MappingRequest r = new MappingRequest();
        r.setIds(List.of(new Identifier(IdentifierType.GENE, "BRCA2")));
        assertFalse(repo.servesIds(r), "single identifier, no filter: MappingRepo");
        r.setSort("cadd");
        assertTrue(repo.servesIds(r));
        r.setSort(null); r.setPocket(true);
        assertTrue(repo.servesIds(r));
        r.setPocket(null);
        r.setIds(List.of(new Identifier(IdentifierType.GENE, "BRCA2"), new Identifier(IdentifierType.UNIPROT, "P22304")));
        assertTrue(repo.servesIds(r));
    }
}
