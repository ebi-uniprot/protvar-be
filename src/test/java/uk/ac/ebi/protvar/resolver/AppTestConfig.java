package uk.ac.ebi.protvar.resolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.ac.ebi.pdbe.api.PDBeAPI;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.uniprot.coordinates.api.CoordinatesAPI;
import uk.ac.ebi.uniprot.coordinates.model.DataServiceCoordinate;
import uk.ac.ebi.uniprot.proteins.api.ProteinsAPI;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.uniprot.variation.api.VariationAPI;
import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
@Profile({ "test" })
@SpringBootApplication
public class AppTestConfig {

	@Bean
	@Profile({"test"})
	ProteinsAPI proteinsAPI() {
		return new ProteinsAPI() {
			@Override
			public DataServiceProtein[] getProtein(String accessions) {
				FileInputStream fis;
				try {
					String proteinJsonFile = "src/test/resources/jsons/protein.json";
					if ("P68431".equalsIgnoreCase(accessions)) {
						proteinJsonFile = "src/test/resources/merge/protein_P68431.json";
					}
					String data = Files.readString(Path.of(proteinJsonFile));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceProtein[] dsp = gson.fromJson(data, DataServiceProtein[].class);
					return dsp;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}
		};

	}

	@Bean
	@Profile({"test"})
	VariationAPI variationAPI() {
		return new VariationAPI() {
			@Override
			public DataServiceVariation[] getVariationByParam(String value, String param) {
				if (value.contains("88888888")) {
					throw new RuntimeException("connection failed, unable to reach uniprot variation api");
				}
				try {
					String data = Files.readString(Path.of("src/test/resources/jsons/variation.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceVariation[] dsv = gson.fromJson(data, DataServiceVariation[].class);
					return dsv;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}

			@Override
			public DataServiceVariation[] getVariationByAccession(String accession, String location) {
				try {
					String variationJsonFile = "src/test/resources/variation.json";
					if ("P68431".equalsIgnoreCase(accession)) {
						variationJsonFile = "src/test/resources/merge/variation_P68431.json";
					}
					String data = Files.readString(Path.of(variationJsonFile));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceVariation[] dsv = gson.fromJson(data, DataServiceVariation[].class);
					return dsv;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}
		};
	}

	@Bean
	@Profile({"test"})
	CoordinatesAPI coordinatesAPI() {
		return new CoordinatesAPI() {
			/*
			@Override
			public DataServiceCoordinate[] getGene(UserInput userInput) {
				try {
					String data = Files.readString(Path.of("src/test/resources/jsons/coordinate.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
					return dsc;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;

			}*/
			@Override
			public DataServiceCoordinate[] getCoordinateByAccession(String accession) {
				try {
					String data = Files.readString(Path.of("src/test/resources/merge/coordinate_P68431.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
					return dsc;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}

			@Override
			public DataServiceCoordinate[] getCoordinates(String geneName, String chromosome, int offset, int pageSize,
														  String location) {
				try {
					String data = Files.readString(Path.of("src/test/resources/merge/coordinate_P68431.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
					return dsc;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	@Bean
	@Profile({"test"})
	PDBeAPI pdBeAPI() {
		return new PDBeAPI() {
			@Override
			public List<PDBeStructureResidue> get(String accession, int position) {
				return null;
			}
			/*
			@Override
			public Object[] get(List<PDBeRequest> requests) {
				try {
					String data = Files.readString(Path.of("src/test/resources/jsons/pdbe.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					Object[] dsc = gson.fromJson(data, Object[].class);
					return dsc;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			} */
		};
	}

}