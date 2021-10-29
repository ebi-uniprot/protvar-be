package uk.ac.ebi.protvar.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.protvar.ApplicationMainClass;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApplicationMainClass.class)
@AutoConfigureMockMvc
public class ControllerIT {

	@Autowired
	private MockMvc mvc;

	@Test
	public void hgvs_post_test_stream() throws Exception {
		mvc.perform(post("/variant/mapping").contentType(MediaType.APPLICATION_JSON)
				.content("[\"NC_000014.9:g.89993420A>G\",\"NC_000010.11:g.87933147C>G\"]").param("size", "-1"))
				.andExpect(status().isOk());
	}

	@Test
	public void hgvs_post_test() throws Exception {
		mvc.perform(post("/variant/mapping").contentType(MediaType.APPLICATION_JSON)
				.content("[\"NC_000014.9:g.89993420A>G\",\"NC_000010.11:g.87933147C>G\"]").param("size", "1"))
				.andExpect(status().isOk());
	}

	@Test
	public void dbSNP_post_test() throws Exception {
		mvc.perform(post("/variant/mapping").contentType(MediaType.APPLICATION_JSON)
				.content("[\"rs121909224\",\"rs121909229\"]").param("size", "-1"))
				.andExpect(status().isOk());
	}
	
	@Test
	public void molecule_type_protein_get_test() throws Exception {
		mvc.perform(get("/variant/function/P21802/12345").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
	
	@Test
	public void molecule_type_structure_get_test() throws Exception {
		mvc.perform(get("/variant/structure/1d5r/12345").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
	
	@Test
	public void mapping_get_test() throws Exception {
		mvc.perform(get("/variant/mapping/14/89993420?refAllele=A&altAllele=G").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
	
	@Test
	public void mapping_post_test() throws Exception {
		mvc.perform(post("/variant/mapping").contentType(MediaType.APPLICATION_JSON)
				.content("[\"14 89993420 rs1245551 A G\",\"21 25891796 rs1245551 C T\"]"))
				.andExpect(status().isOk());
	}
	
	@Test
	public void structure_get_test() throws Exception {
		mvc.perform(get("/variant/structure/P60484/"+130).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
