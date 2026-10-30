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
        ReflectionTestUtils.setField(repo, "proteinTable", "prot");
        ReflectionTestUtils.setField(repo, "transcriptTable", "tx");
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
        String sql = repo.idPositions(ids(new Identifier(IdentifierType.UNIPROT, "P22304"),
                new Identifier(IdentifierType.GENE, "BRCA2")), p);
        assertEquals("""
                SELECT DISTINCT m.chromosome AS id_chr, m.genomic_position AS id_pos FROM map m WHERE m.accession = :id0
                UNION SELECT DISTINCT m.chromosome AS id_chr, m.genomic_position AS id_pos FROM map m WHERE m.accession IN (SELECT accession FROM prot WHERE gene_name = :id1)""",
                sql);
        assertEquals("P22304", p.getValue("id0"));
        assertEquals("BRCA2", p.getValue("id1"));
    }

    @Test
    void idPositionsEnsemblVersionAndRefseq() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = repo.idPositions(ids(new Identifier(IdentifierType.ENSEMBL, "enst00000380152.8"),
                new Identifier(IdentifierType.ENSEMBL, "ENSG00000139618.2"),
                new Identifier(IdentifierType.REFSEQ, "NP_000050")), p);
        assertTrue(sql.contains("WHERE m.enst = :id0 AND m.enstv = :id0v"));
        assertEquals("ENST00000380152", p.getValue("id0"));
        assertEquals("8", p.getValue("id0v"));
        assertTrue(sql.contains("WHERE (m.accession, m.enst) IN (SELECT accession, enst FROM tx WHERE ensg = :id1)\n"),
                "ENSG matches the base id only, through the transcript table");
        assertTrue(sql.contains("FROM refseq WHERE refseq_acc ILIKE :id2 || '.%'"));
    }

    @Test
    void idPositionsPdbJoinsObservedRegions() {
        String sql = repo.idPositions(ids(new Identifier(IdentifierType.PDB, "1ABC")), new MapSqlParameterSource());
        assertTrue(sql.contains("JOIN (SELECT DISTINCT accession, observed_regions FROM str WHERE pdb_id = LOWER(:id0)) s"));
        assertTrue(sql.contains("m.accession = s.accession AND"));
        assertTrue(sql.contains("observed_regions"));
    }

    @Test
    void idPositionsUnmatchableIdentifierAddsNothing() {
        String sql = repo.idPositions(ids(new Identifier(IdentifierType.ENSEMBL, "ENSX123")), new MapSqlParameterSource());
        assertTrue(sql.endsWith("WHERE false"));
    }

    @Test
    void idPositionsRangeOnSingleUniprotOnly() {
        MappingRequest r = ids(new Identifier(IdentifierType.UNIPROT, "P22304"));
        r.setStartPos(600); r.setEndPos(558);
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertTrue(repo.idPositions(r, p).endsWith("WHERE m.accession = :id0 AND m.protein_position BETWEEN :startPos AND :endPos"));
        assertEquals(558, p.getValue("startPos"));
        assertEquals(600, p.getValue("endPos"));

        r = ids(new Identifier(IdentifierType.GENE, "BRCA2"));
        r.setStartPos(1); r.setEndPos(10);
        assertFalse(repo.idPositions(r, new MapSqlParameterSource()).contains("BETWEEN"), "not a single UniProt browse");
    }

    private static MappingRequest ids(Identifier... ids) {
        MappingRequest r = new MappingRequest();
        r.setIds(List.of(ids));
        return r;
    }



    @Test
    void idPositionsEnspWithVersionThroughTranscripts() {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = repo.idPositions(ids(new Identifier(IdentifierType.ENSEMBL, "ENSP00000269305.4")), p);
        assertTrue(sql.endsWith("WHERE (m.accession, m.enst) IN (SELECT accession, enst FROM tx WHERE ensp = :id0 AND enspv = :id0v)"));
        assertEquals("4", p.getValue("id0v"));
    }
}
