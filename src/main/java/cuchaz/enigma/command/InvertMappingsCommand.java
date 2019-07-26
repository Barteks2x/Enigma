package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.utils.SupplierWithThrowable;
import cuchaz.enigma.utils.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InvertMappingsCommand extends Command {
    public InvertMappingsCommand() {
        super("invert-mappings");
    }

    @Override
    public String getUsage() {
        return "<source-format:reader> <source> <result-format:writer> <result> [<in-jar>]";
    }

    @Override
    public boolean isValidArgument(int length) {
        return length == 4 || length == 5;
    }

    @Override
    public void run(String... args) throws IOException, MappingParseException {
        Enigma enigma = Enigma.create();
        Path jarPath = getReadablePath(getArg(args, 4, "in jar", false));
        SupplierWithThrowable<JarIndex, IOException> jarIndexSupplier = getJarIndexSupplier(enigma, jarPath);

        EntryTree<EntryMapping> source = MappingCommandsUtil.read(enigma, args[0], Paths.get(args[1]), jarIndexSupplier);
        EntryTree<EntryMapping> result = MappingCommandsUtil.invert(source);

        Path output = Paths.get(args[3]);
        Utils.delete(output);
        MappingCommandsUtil.write(enigma, result, args[2], output, jarIndexSupplier);
    }
}
