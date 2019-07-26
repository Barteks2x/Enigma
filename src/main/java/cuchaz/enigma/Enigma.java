/*******************************************************************************
 * Copyright (c) 2015 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public
 * License v3.0 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Contributors:
 * Jeff Martin - initial API and implementation
 ******************************************************************************/

package cuchaz.enigma;

import static cuchaz.enigma.utils.Utils.defaultToString;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import cuchaz.enigma.analysis.ClassCache;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.service.EnigmaService;
import cuchaz.enigma.api.service.EnigmaServiceFactory;
import cuchaz.enigma.api.service.EnigmaServiceType;
import cuchaz.enigma.api.service.JarIndexerService;
import cuchaz.enigma.translation.mapping.serde.BuiltinMappingFormats;
import cuchaz.enigma.translation.mapping.serde.MappingsFormat;
import cuchaz.enigma.translation.mapping.serde.MappingsFormats;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class Enigma {
	private final EnigmaProfile profile;
	private final EnigmaServices services;
	private final MappingsFormats mappingsFormats;

	private Enigma(EnigmaProfile profile, EnigmaServices services, MappingsFormats mappingsFormats) {
		this.profile = profile;
		this.services = services;
        this.mappingsFormats = mappingsFormats;
    }

	public static Enigma create() {
		return new Builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public EnigmaProject openJar(Path path, ProgressListener progress) throws IOException {
		ClassCache classCache = ClassCache.of(path);
		JarIndex jarIndex = classCache.index(progress);

		services.get(JarIndexerService.TYPE).ifPresent(indexer -> {
			indexer.acceptJar(classCache, jarIndex);
		});

		return new EnigmaProject(this, classCache, jarIndex);
	}

	public EnigmaProfile getProfile() {
		return profile;
	}

	public EnigmaServices getServices() {
		return services;
	}

    public MappingsFormats getMappingsFormats() {
        return mappingsFormats;
    }

    public static class Builder {
		private EnigmaProfile profile = EnigmaProfile.EMPTY;
		private Iterable<EnigmaPlugin> plugins = ServiceLoader.load(EnigmaPlugin.class);

		private Builder() {
		}

		public Builder setProfile(EnigmaProfile profile) {
			Preconditions.checkNotNull(profile, "profile cannot be null");
			this.profile = profile;
			return this;
		}

		public Builder setPlugins(Iterable<EnigmaPlugin> plugins) {
			Preconditions.checkNotNull(plugins, "plugins cannot be null");
			this.plugins = plugins;
			return this;
		}

		public Enigma build() {
			PluginContext pluginContext = new PluginContext(profile);
			for (EnigmaPlugin plugin : plugins) {
				plugin.init(pluginContext);
			}

			EnigmaServices services = pluginContext.buildServices();
			MappingsFormats formats = pluginContext.buildMappingsFormats();
			return new Enigma(profile, services, formats);
		}
	}

	private static class PluginContext implements EnigmaPluginContext {
		private final EnigmaProfile profile;

        private final ImmutableMap.Builder<EnigmaServiceType<?>, EnigmaService> services = ImmutableMap.builder();
        private final Map<String, MappingsFormat> mappingsFormats = new HashMap<>();

		PluginContext(EnigmaProfile profile) {
			this.profile = profile;

            for (BuiltinMappingFormats value : BuiltinMappingFormats.values()) {
                registerMappingsFormat(value.toString(), value);
            }
		}

		@Override
		public <T extends EnigmaService> void registerService(String id, EnigmaServiceType<T> serviceType, EnigmaServiceFactory<T> factory) {
			EnigmaProfile.Service serviceProfile = profile.getServiceProfile(serviceType);

			// if this service type is not configured, or it is configured to use a different service id, skip
			if (serviceProfile == null || !serviceProfile.matches(id)) return;

			T service = factory.create(serviceProfile::getArgument);
			services.put(serviceType, service);
		}

        @Override
        public void registerMappingsFormat(String id, MappingsFormat format) {
            if (mappingsFormats.containsValue(format)) {
                throw new IllegalArgumentException("Mappings format " + defaultToString(format) + " already registered");
            }
            if (mappingsFormats.containsKey(id)) {
                throw new IllegalArgumentException("Mappings format with ID " + id + " already registered with value "
                        + defaultToString(mappingsFormats.get(id)) + ", trying to register " + defaultToString(format));
            }
            mappingsFormats.put(id, format);
        }

        EnigmaServices buildServices() {
			return new EnigmaServices(services.build());
		}

        public MappingsFormats buildMappingsFormats() {
            return new MappingsFormats(ImmutableMap.<String, MappingsFormat>builder().putAll(mappingsFormats).build());
        }
    }
}
