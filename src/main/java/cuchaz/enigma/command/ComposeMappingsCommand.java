package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.utils.SupplierWithThrowable;
import cuchaz.enigma.utils.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ComposeMappingsCommand extends Command {
    public ComposeMappingsCommand() {
        super("compose-mappings");
    }

    @Override
    public String getUsage() {
        return "<left-format[:reader]> <left> <right-format[:reader]> <right> <result-format[:writer]> <result> <keep-mode> [<in-jar>]";
    }

    @Override
    public boolean isValidArgument(int length) {
        return length == 7 || length == 8;
    }

    @Override
    public void run(String... args) throws IOException, MappingParseException {
        Enigma enigma = Enigma.create();
        Path jarPath = getReadableFile(getArg(args, 7, "in jar", false)).toPath();
        SupplierWithThrowable<JarIndex, IOException> getJarIndex = getJarIndexSupplier(enigma, jarPath);

        EntryTree<EntryMapping> left = MappingCommandsUtil.read(enigma, args[0], Paths.get(args[1]), getJarIndex);
        // TODO: remap jar index to be remapped with "left" mappings?
        EntryTree<EntryMapping> right = MappingCommandsUtil.read(enigma, args[2], Paths.get(args[3]), getJarIndex);
        EntryTree<EntryMapping> result = MappingCommandsUtil.compose(left, right, args[6].equals("left") || args[6].equals("both"), args[6].equals("right") || args[6].equals("both"));

        Path output = Paths.get(args[5]);
        Utils.delete(output);
        MappingCommandsUtil.write(enigma, result, args[4], output, getJarIndex);
    }
}
