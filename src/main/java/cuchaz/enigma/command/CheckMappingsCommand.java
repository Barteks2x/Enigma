package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckMappingsCommand extends Command {

	public CheckMappingsCommand() {
		super("checkmappings");
	}

	@Override
	public String getUsage() {
		return "<in jar> <mappings file> [<mappings-format[:reader]>]";
	}

	@Override
	public boolean isValidArgument(int length) {
		return length == 2 || length == 3;
	}

	@Override
	public void run(String... args) throws Exception {
		Path fileJarIn = getReadableFile(getArg(args, 0, "in jar", true)).toPath();
		Path fileMappings = getReadablePath(getArg(args, 1, "mappings file", true));
		String format = args.length >= 3 ? args[2] : null;

		Enigma enigma = Enigma.create();

		System.out.println("Reading JAR...");

		EnigmaProject project = enigma.openJar(fileJarIn, ProgressListener.none());

		System.out.println("Reading mappings...");

		EntryTree<EntryMapping> mappings = MappingCommandsUtil.read(enigma, format, fileMappings, project::getJarIndex);
		project.setMappings(mappings);

		JarIndex idx = project.getJarIndex();

		boolean error = false;

		for (Set<ClassEntry> partition : idx.getPackageVisibilityIndex().getPartitions()) {
			long packages = partition.stream()
					.map(project.getMapper()::deobfuscate)
					.map(ClassEntry::getPackageName)
					.distinct()
					.count();
			if (packages > 1) {
				error = true;
				System.err.println("ERROR: Must be in one package:\n" + partition.stream()
						.map(project.getMapper()::deobfuscate)
						.map(ClassEntry::toString)
						.sorted()
						.collect(Collectors.joining("\n"))
				);
			}
		}

		if (error) {
			throw new IllegalStateException("Errors in package visibility detected, see SysErr above");
		}
	}
}
