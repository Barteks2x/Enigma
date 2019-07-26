package cuchaz.enigma;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import cuchaz.enigma.api.service.EnigmaServiceType;
import cuchaz.enigma.translation.mapping.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsOption;

import java.io.Reader;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public final class EnigmaProfile {
	public static final EnigmaProfile EMPTY = new EnigmaProfile(ImmutableMap.of());

	private static final Gson GSON = new Gson();

	@SerializedName("services")
	private final Map<String, Service> serviceProfiles;

	// TODO: remove this, replaced with mappingsOptions
	@SerializedName("mapping_save_parameters")
	private final MappingSaveParameters mappingSaveParameters = null;

	@SerializedName("mappings_options")
	private final Map<String, String> mappingsOptions = new HashMap<>();

	private EnigmaProfile(Map<String, Service> serviceProfiles) {
		this.serviceProfiles = serviceProfiles;
	}

	public static EnigmaProfile parse(Reader reader) {
		return GSON.fromJson(reader, EnigmaProfile.class);
	}

	@Nullable
	public Service getServiceProfile(EnigmaServiceType<?> serviceType) {
		return serviceProfiles.get(serviceType.key);
	}

	public Map<MappingsOption, String> getMappingsOptions(Set<MappingsOption> allOptions) {
		return allOptions.stream()
				.filter(x -> mappingsOptions.containsKey(x.getName()) || x.getName().equals("file_name_format"))
				.map(option -> new SimpleEntry<>(option, getOption(option)))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private String getOption(MappingsOption option) {
		if (option.getName().equals("file_name_format") && !mappingsOptions.containsKey("file_name_format")) {
			return mappingSaveParameters == null ? option.getDefaultValue() : mappingSaveParameters.getFileNameFormat().toString();
		}
		String val = mappingsOptions.getOrDefault(option.getName(), option.getDefaultValue());
		if (option.isRequired() && val == null) {
			throw new IllegalStateException("Mapping option " + option.getName() + " is required but isn't set");
		}
		return val;
	}

	public static class Service {
		private final String id;
		private final Map<String, String> args;

		Service(String id, Map<String, String> args) {
			this.id = id;
			this.args = args;
		}

		public boolean matches(String id) {
			return this.id.equals(id);
		}

		public Optional<String> getArgument(String key) {
			return args != null ? Optional.ofNullable(args.get(key)) : Optional.empty();
		}
	}
}
