package cuchaz.enigma.api;

import cuchaz.enigma.api.service.EnigmaService;
import cuchaz.enigma.api.service.EnigmaServiceFactory;
import cuchaz.enigma.api.service.EnigmaServiceType;
import cuchaz.enigma.translation.mapping.serde.MappingsFormat;

public interface EnigmaPluginContext {
	<T extends EnigmaService> void registerService(String id, EnigmaServiceType<T> serviceType, EnigmaServiceFactory<T> factory);

	void registerMappingsFormat(String id, MappingsFormat format);
}
