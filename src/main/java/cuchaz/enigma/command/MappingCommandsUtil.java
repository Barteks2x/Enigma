package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.MappingTranslator;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.VoidEntryResolver;
import cuchaz.enigma.translation.mapping.serde.BuiltinMappingFormats;
import cuchaz.enigma.translation.mapping.serde.MappingsFormat;
import cuchaz.enigma.translation.mapping.serde.MappingsFormats;
import cuchaz.enigma.translation.mapping.serde.MappingsOption;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.MappingsWriter;
import cuchaz.enigma.translation.mapping.serde.TinyMappingsWriter;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.EntryTreeNode;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.SupplierWithThrowable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MappingCommandsUtil {
    private MappingCommandsUtil() {}

    public static EntryTree<EntryMapping> invert(EntryTree<EntryMapping> mappings) {
        Translator translator = new MappingTranslator(mappings, VoidEntryResolver.INSTANCE);
        EntryTree<EntryMapping> result = new HashEntryTree<>();

        for (EntryTreeNode<EntryMapping> node : mappings) {
            Entry<?> leftEntry = node.getEntry();
            EntryMapping leftMapping = node.getValue();

            if (!(leftEntry instanceof ClassEntry || leftEntry instanceof MethodEntry || leftEntry instanceof FieldEntry)) {
                result.insert(translator.translate(leftEntry), leftMapping);
                continue;
            }

            Entry<?> rightEntry = translator.translate(leftEntry);

            result.insert(rightEntry, leftMapping == null ? null : new EntryMapping(leftEntry.getName())); // TODO: leftMapping.withName once javadoc PR is merged
        }

        return result;
    }

    public static EntryTree<EntryMapping> compose(EntryTree<EntryMapping> left, EntryTree<EntryMapping> right, boolean keepLeftOnly, boolean keepRightOnly) {
        Translator leftTranslator = new MappingTranslator(left, VoidEntryResolver.INSTANCE);
        EntryTree<EntryMapping> result = new HashEntryTree<>();
        Set<Entry<?>> addedMappings = new HashSet<>();

        for (EntryTreeNode<EntryMapping> node : left) {
            Entry<?> leftEntry = node.getEntry();
            EntryMapping leftMapping = node.getValue();

            Entry<?> rightEntry = leftTranslator.translate(leftEntry);

            EntryMapping rightMapping = right.get(rightEntry);
            if (rightMapping != null) {
                result.insert(leftEntry, rightMapping);
                addedMappings.add(rightEntry);
            } else if (keepLeftOnly) {
                result.insert(leftEntry, leftMapping);
            }
        }

        if (keepRightOnly) {
            Translator leftInverseTranslator = new MappingTranslator(invert(left), VoidEntryResolver.INSTANCE);
            for (EntryTreeNode<EntryMapping> node : right) {
                Entry<?> rightEntry = node.getEntry();
                EntryMapping rightMapping = node.getValue();

                if (!addedMappings.contains(rightEntry)) {
                    result.insert(leftInverseTranslator.translate(rightEntry), rightMapping);
                }
            }
        }
        return result;
    }

    /**
     * Reads mappings in a given format from the specified path.
     *
     * @param enigma Initialized {@link Enigma} instance, to use data from plugins and profiles
     * @param formatSpec Specifies mappings format in form "format-name:reader-name", or "format-name" to use default reader
     * @param path File or directory to load mappings from
     * @param getJarIndex Supplies a {@link JarIndex} instance upon request.
     * A supplier is provided instead of value to avoid loading a jar when not needed
     * @return {@link EntryTree} with newly loaded mappings\
     */
    public static EntryTree<EntryMapping> read(Enigma enigma, String formatSpec, Path path,
            SupplierWithThrowable<JarIndex, IOException> getJarIndex) throws MappingParseException, IOException {
        if (formatSpec == null) {
            formatSpec = BuiltinMappingFormats.ENIGMA.toString();
        }
        String[] formatParts = formatSpec.split(":");

        String formatName = formatParts[0];

        MappingsFormats formats = enigma.getMappingsFormats();
        MappingsFormat format = formats.get(formatName);

        if (format == null) {
            throw new IllegalArgumentException("No format " + formatName);
        }

        MappingsReader reader;
        if (formatParts.length == 2) {
            reader = format.getReaders().get(formatParts[1]);
            if (reader == null) {
                throw new IllegalArgumentException("No reader " + formatParts[1] + " for format " + formatName);
            }
        } else {
            reader = format.getDefaultReaderFor(path);
            if (reader == null) {
                throw new IllegalArgumentException("No default reader for format " + formatName + " for path " + path + " and no reader specified.");
            }
        }

        Map<MappingsOption, String> options = enigma.getProfile().getMappingsOptions(reader.getAllOptions());
        return reader.read(path, ProgressListener.none(), options, getJarIndex);
    }

    /**
     * Writes mappings in a given format to the specified path.
     *
     * @param enigma Initialized {@link Enigma} instance, to use data from plugins and profiles
     * @param mappings Mappings to write
     * @param formatSpec Specifies mappings format in form "format-name:writer-name", or "format-name" to use default writer
     * @param path File or directory to write mappings to
     * @param getJarIndex Supplies a {@link JarIndex} instance upon request.
     * A supplier is provided instead of value to avoid loading a jar when not needed
     */
    public static void write(Enigma enigma, EntryTree<EntryMapping> mappings, String formatSpec, Path path,
            SupplierWithThrowable<JarIndex, IOException> getJarIndex) {
        if (formatSpec == null) {
            formatSpec = BuiltinMappingFormats.ENIGMA.toString();
        }
        String[] formatParts = formatSpec.split(":");

        String formatName = formatParts[0];
        MappingsFormats formats = enigma.getMappingsFormats();
        MappingsFormat format = formats.get(formatName);
        if (format == null) {
            throw new IllegalArgumentException("No format " + formatName);
        }
        MappingsWriter writer;
        if (formatParts.length == 2) {
            writer = format.getWriters().get(formatParts[1]);
            if (writer == null) {
                throw new IllegalArgumentException("No writer " + formatParts[1] + " for format " + formatName);
            }
        } else {
            writer = format.getDefaultWriterFor(path);
            if (writer == null) {
                throw new IllegalArgumentException("No default writer for format " + formatName + " for path " + path + " and no writer specified.");
            }
        }

        Map<MappingsOption, String> opts = enigma.getProfile().getMappingsOptions(writer.getAllOptions());
        // TODO: remove this hardcoded check, add special command parameter(s) for mappingsOptions
        if (format == BuiltinMappingFormats.TINY && (!opts.containsKey(TinyMappingsWriter.NAME_DEOBF) || !opts.containsKey(TinyMappingsWriter.NAME_OBF))) {
            String[] split = formatName.split(":");

            if (split.length != 3) {
                throw new IllegalArgumentException("specify column names as 'tiny:from_column:to_column'");
            }

            opts.put(TinyMappingsWriter.NAME_OBF, split[1]);
            opts.put(TinyMappingsWriter.NAME_DEOBF, split[2]);
        }

        writer.write(mappings, path, ProgressListener.none(), opts, getJarIndex);
    }
}
