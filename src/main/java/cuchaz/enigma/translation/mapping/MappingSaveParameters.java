package cuchaz.enigma.translation.mapping;

import com.google.gson.annotations.SerializedName;

/**
 * @deprecated Use {@link cuchaz.enigma.translation.mapping.serde.MappingsOption} instead
 */
@Deprecated
public class MappingSaveParameters {
	@SerializedName("file_name_format")
	private final MappingFileNameFormat fileNameFormat;

	public MappingSaveParameters(MappingFileNameFormat fileNameFormat) {
		this.fileNameFormat = fileNameFormat;
	}

	public MappingFileNameFormat getFileNameFormat() {
		return fileNameFormat;
	}
}
