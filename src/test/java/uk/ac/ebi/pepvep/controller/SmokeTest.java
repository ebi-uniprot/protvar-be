package uk.ac.ebi.pepvep.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import uk.ac.ebi.pepvep.controller.APIController;

@SpringBootTest
public class SmokeTest {

	@Autowired
	private APIController apiController;

	@Test
	public void contextLoads() throws Exception {
		assertThat(apiController).isNotNull();
	}
}
