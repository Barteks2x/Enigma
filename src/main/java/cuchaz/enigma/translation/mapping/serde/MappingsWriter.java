package cuchaz.enigma.translation.mapping.serde;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.utils.SupplierWithThrowable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface MappingsWriter {

	default Set<MappingsOption> getAllOptions() {
		return Collections.emptySet();
	}

	EnumSet<PathType> getSupportedPathTypes();

	default boolean checkPath(Path path, Consumer<String> errorConsumer) {
		EnumSet<PathType> types = getSupportedPathTypes();
		if (types.isEmpty()) {
			errorConsumer.accept("This writer doesn't support any files!");
			return false;
		}
		for (PathType type : getSupportedPathTypes()) {
			if (type.test(path)) {
				return true;
			}
		}
		errorConsumer.accept(String.format("\"%s\" is not one of: %s", path, types.toString()));
		return false;
	}

	void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progress,
			Map<MappingsOption, String> options, SupplierWithThrowable<JarIndex, IOException> jarIndex);

	default void write(EntryTree<EntryMapping> mappings, Path path, ProgressListener progress,
			Map<MappingsOption, String> options, SupplierWithThrowable<JarIndex, IOException> jarIndex) {
		write(mappings, MappingDelta.added(mappings), path, progress, options, jarIndex);
	}
}
