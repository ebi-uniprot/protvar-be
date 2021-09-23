package uk.ac.ebi.pepvep.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.pepvep.model.api.DataServiceCoordinate;
import uk.ac.ebi.pepvep.model.api.DataServiceProtein;
import uk.ac.ebi.pepvep.model.api.DataServiceVariation;

public class TestUtils {

	public static DataServiceVariation[] getVariation(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, DataServiceVariation[].class);
	}

	public static DataServiceProtein[] getProtein(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, DataServiceProtein[].class);
	}

	public static DataServiceCoordinate[] getGene(String filePath) throws IOException {
		String data = Files.readString(Path.of(filePath));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.fromJson(data, DataServiceCoordinate[].class);
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
