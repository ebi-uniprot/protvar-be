package uk.ac.ebi.protvar.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.uniprot.domain.features.ProteinFeatureInfo;

public class TestUtils {

	public static ProteinFeatureInfo[] getVariation(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, ProteinFeatureInfo[].class);
	}

	public static UPEntry[] getProtein(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, UPEntry[].class);
	}

	public static Object[] getStructure(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, Object[].class);
	}

	public static Object getMapping(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, Object.class);
	}
}
