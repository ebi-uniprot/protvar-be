package uk.ac.ebi.protvar.resolver;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.protvar.model.PDBeRequest;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;
import uk.ac.ebi.protvar.model.response.PDBeStructure;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;

@Configuration
@Profile({ "test" })
@SpringBootApplication
public class AppTestConfig {

	@Bean
	@Profile({ "test" })
	UniprotAPIRepo uniprotAPIRepoImpl() {
		return new UniprotAPIRepo() {
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
			public Object[] getPDBe(List<PDBeRequest> requests) {
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

			@Override
			public String getUniproAccession(String accession) {
				return "P68431";
			}

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

			@Override
			public List<PDBeStructure> getPDBeStructure(String accession, int aaPosition) {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}
}
