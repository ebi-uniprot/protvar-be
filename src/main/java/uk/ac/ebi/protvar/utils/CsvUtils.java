package uk.ac.ebi.protvar.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class CsvUtils {

	public static String getValOrNA(String value) {
		if (StringUtils.isEmpty(value))
			return Constants.NA;
		return value;
	}
	public static String getValOrNA(List<String> list) {
		if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
			return list.get(0);
		}
		return Constants.NA;
	}
	public static String getValOrNA(Double value) {
		if (value == null) return Constants.NA;
		return getValOrNA(value.toString());
	}
}
