package uk.ac.ebi.pepvep.utils;

import org.apache.commons.lang3.StringUtils;

public class CSVUtils {
	
	public static String getValOrNA(String value) {
		if (StringUtils.isEmpty(value))
			return Constants.NA;
		return value;
	}
}
