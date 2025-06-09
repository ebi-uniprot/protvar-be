package uk.ac.ebi.protvar.resolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.protvar.api.ProteinsAPI;
import uk.ac.ebi.protvar.api.VariationAPI;
import uk.ac.ebi.uniprot.domain.features.ProteinFeatureInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Profile({ "test" })
@SpringBootApplication
public class AppTestConfig {

	@Bean
	@Profile({"test"})
	ProteinsAPI proteinsAPI() {
		return new ProteinsAPI() {
			public UPEntry[] getProtein(String accessions) {
				FileInputStream fis;
				try {
					String proteinJsonFile = "src/test/resources/jsons/protein.json";
					if ("P68431".equalsIgnoreCase(accessions)) {
						proteinJsonFile = "src/test/resources/merge/protein_P68431.json";
					}
					String data = Files.readString(Path.of(proteinJsonFile));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					UPEntry[] upEntries = gson.fromJson(data, UPEntry[].class);
					return upEntries;
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
			public ProteinFeatureInfo[] getVariationByParam(String value, String param) {
				if (value.contains("88888888")) {
					throw new RuntimeException("connection failed, unable to reach uniprot variation api");
				}
				try {
					String data = Files.readString(Path.of("src/test/resources/jsons/variation.json"));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					ProteinFeatureInfo[] proteinFeatureInfos = gson.fromJson(data, ProteinFeatureInfo[].class);
					return proteinFeatureInfos;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}

			public ProteinFeatureInfo[] getVariation(String accession, int location) {
				try {
					String variationJsonFile = "src/test/resources/variation.json";
					if ("P68431".equalsIgnoreCase(accession)) {
						variationJsonFile = "src/test/resources/merge/variation_P68431.json";
					}
					String data = Files.readString(Path.of(variationJsonFile));
					GsonBuilder builder = new GsonBuilder();
					Gson gson = builder.create();
					ProteinFeatureInfo[] proteinFeatureInfos = gson.fromJson(data, ProteinFeatureInfo[].class);
					return proteinFeatureInfos;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}

			public ProteinFeatureInfo[] getVariation(String accessions) {
				return new ProteinFeatureInfo[0];
			}

			public ProteinFeatureInfo[] getVariationAccessionLocations(String accLocs) {
				return new ProteinFeatureInfo[0];
			}
		};
	}

}