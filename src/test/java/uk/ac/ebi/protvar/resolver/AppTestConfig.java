package uk.ac.ebi.protvar.resolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.protvar.api.ProteinsAPI;

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

}