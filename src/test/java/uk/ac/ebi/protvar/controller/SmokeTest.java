package uk.ac.ebi.protvar.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SmokeTest {

	@Autowired
	private AnnotationController annotationController;

	@Test
	public void contextLoads() throws Exception {
		assertThat(annotationController).isNotNull();
	}
}
