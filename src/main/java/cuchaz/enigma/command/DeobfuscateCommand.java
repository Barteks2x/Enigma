package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;

import java.nio.file.Path;

public class DeobfuscateCommand extends Command {

	public DeobfuscateCommand() {
		super("deobfuscate");
	}

	@Override
	public String getUsage() {
		return "<in jar> <out jar> [<mappings file> <mappings-format[:reader>]]";
	}

	@Override
	public boolean isValidArgument(int length) {
		return length == 2 || length == 3;
	}

	@Override
	public void run(String... args) throws Exception {
		Path fileJarIn = getReadablePath(getArg(args, 0, "in jar", true));
		Path fileJarOut = getWritableFile(getArg(args, 1, "out jar", true)).toPath();
		Path fileMappings = getReadablePath(getArg(args, 2, "mappings file", false));
		String formatString = args.length >= 4 ? args[3] : null;

		Enigma enigma = Enigma.create();
		EnigmaProject project = openJar(enigma, fileJarIn);
		openMappings(project, fileMappings, formatString);

		ProgressListener progress = new ConsoleProgressListener();

		EnigmaProject.JarExport jar = project.exportRemappedJar(progress);
		jar.write(fileJarOut, progress);
	}
}
