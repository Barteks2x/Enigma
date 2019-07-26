package cuchaz.enigma.translation.mapping.serde;

import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nullable;

public interface MappingsFormat {

    @Nullable MappingsWriter getDefaultWriterFor(MappingsReader reader, Path output);

    @Nullable default MappingsWriter getDefaultWriterFor(Path output) {
        for (MappingsWriter writer : getWriters().values()) {
            boolean supportedType = writer.getSupportedPathTypes().stream().anyMatch(pathType -> pathType.test(output));
            if (supportedType && writer.checkPath(output, s -> {})) {
                return writer;
            }
        }
        return null;
    }

    @Nullable default MappingsReader getDefaultReaderFor(Path path) {
        for (MappingsReader reader : getReaders().values()) {
            boolean supportedType = reader.getSupportedPathTypes().stream().anyMatch(pathType -> pathType.test(path));
            if (supportedType && reader.checkPath(path, s -> {})) {
                return reader;
            }
        }
        return null;
    }

    Map<String, MappingsReader> getReaders();

    Map<String, MappingsWriter> getWriters();
}
