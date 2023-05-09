package uk.ac.ebi.pdbe.api;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@AllArgsConstructor
public class PDBeAPIImpl implements PDBeAPI {
    private static final Logger logger = LoggerFactory.getLogger(PDBeAPIImpl.class);

    private RestTemplate pdbeRestTemplate;

    @Override
    public List<PDBeStructureResidue> get(String accession, int position) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.pdbeRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().path(accession).path("/").path(String.valueOf(position)).path("/")
                .path(String.valueOf(++position));
        List<PDBeStructureResidue> structures = new ArrayList<>();

        try {
            ResponseEntity<Object> response = this.pdbeRestTemplate.getForEntity(uriBuilder.build(), Object.class);
            if (response.getBody() != null && response.getBody() instanceof Map) {
                Map<?,?> structure = (Map<?,?>) response.getBody();
                List<Map> accStr = (List<Map>) structure.get(accession);
                accStr.forEach(str -> {
                    PDBeStructureResidue pdbeStr = new PDBeStructureResidue();
                    pdbeStr.setChain_id(str.get("chain_id").toString());
                    pdbeStr.setExperimental_method(str.get("experimental_method").toString());
                    pdbeStr.setPdb_id(str.get("pdb_id").toString());
                    pdbeStr.setResolution(Float.parseFloat(str.get("resolution").toString()));
                    pdbeStr.setStart(Integer.parseInt(str.get("start").toString()));
                    structures.add(pdbeStr);
                });

            }
            return structures;
        } catch (Exception ex) {
            logger.error("Exception calling PDBe API for accession {}, position {}", accession, position);
            return null;
        }

    }

}
