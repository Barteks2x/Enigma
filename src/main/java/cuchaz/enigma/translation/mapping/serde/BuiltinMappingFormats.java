package cuchaz.enigma.translation.mapping.serde;

import static java.util.Collections.emptyMap;

import com.google.common.collect.ImmutableMap;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

public enum BuiltinMappingFormats implements MappingsFormat {
    ENIGMA(ImmutableMap.<String, MappingsWriter>builder()
                    .put("file", EnigmaMappingsWriter.FILE)
                    .put("directory", EnigmaMappingsWriter.DIRECTORY).build(),
            ImmutableMap.<String, MappingsReader>builder()
                    .put("file", EnigmaMappingsReader.FILE)
                    .put("directory", EnigmaMappingsReader.DIRECTORY).build(),
            reader -> reader == EnigmaMappingsReader.FILE ? EnigmaMappingsWriter.FILE : EnigmaMappingsWriter.DIRECTORY),
    TINY(ImmutableMap.<String, MappingsWriter>builder().put("file", TinyMappingsWriter.INSTANCE).build(),
            ImmutableMap.<String, MappingsReader>builder().put("file", TinyMappingsReader.INSTANCE).build(),
            reader -> TinyMappingsWriter.INSTANCE),
    SRG_FILE(ImmutableMap.<String, MappingsWriter>builder().put("file", SrgMappingsWriter.INSTANCE).build(),
            emptyMap(), reader -> SrgMappingsWriter.INSTANCE);

    private final Map<String, MappingsWriter> writers;
    private final Map<String, MappingsReader> readers;
    private final Function<MappingsReader, MappingsWriter> defaultWriter;

    BuiltinMappingFormats(Map<String, MappingsWriter> writers, Map<String, MappingsReader> readers,
            Function<MappingsReader, MappingsWriter> defaultWriter) {
        this.writers = writers;
        this.readers = readers;
        this.defaultWriter = defaultWriter;
    }

    @Nullable @Override public MappingsWriter getDefaultWriterFor(MappingsReader reader, Path path) {
        return defaultWriter.apply(reader);
    }

    @Override public Map<String, MappingsReader> getReaders() {
        return readers;
    }

    @Override public Map<String, MappingsWriter> getWriters() {
        return writers;
    }
}
