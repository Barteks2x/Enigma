package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.BuiltinMappingFormats;
import cuchaz.enigma.translation.mapping.serde.MappingsFormat;
import cuchaz.enigma.translation.mapping.serde.MappingsOption;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.utils.SupplierWithThrowable;
import cuchaz.enigma.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public abstract class Command {
	public final String name;

	protected Command(String name) {
		this.name = name;
	}

	public abstract String getUsage();

	public abstract boolean isValidArgument(int length);

	public abstract void run(String... args) throws Exception;

	protected static SupplierWithThrowable<JarIndex, IOException> getJarIndexSupplier(Enigma enigma, Path jarPath) {
		return Utils.lazyValue(() -> {
			if (jarPath == null) {
				// TODO: more general error message?
				throw new IllegalArgumentException("One of the mapping formats requires specifying a jar file.");
			}
			EnigmaProject project = openJar(enigma, jarPath);
			return project.getJarIndex();
		});
	}

	protected static EnigmaProject openJar(Enigma enigma, Path fileJarIn) throws IOException {
		ProgressListener progress = new ConsoleProgressListener();

		System.out.println("Reading jar...");
		EnigmaProject project = enigma.openJar(fileJarIn, progress);

		return project;
	}

	protected static void openMappings(EnigmaProject project, Path fileMappings, String formatString) throws IOException, MappingParseException {
		ProgressListener progress = new ConsoleProgressListener();

		String[] parts = formatString == null ? null : formatString.split(":");
		if (parts != null && parts.length != 2) {
			throw new IllegalArgumentException("Mappings format must be in form \"formatName:readerName\" but got " + formatString);
		}
		String formatName = formatString == null ? BuiltinMappingFormats.ENIGMA.toString() : parts[0];
		MappingsFormat format = project.getEnigma().getMappingsFormats().get(formatName);
		if (format == null) {
			throw new IllegalArgumentException("Mappings format " + formatName + " doesn't exist");
		}
		MappingsReader reader = formatString == null ? format.getDefaultReaderFor(fileMappings) : format.getReaders().get(parts[1]);
		if (reader == null) {
			reader = format.getDefaultReaderFor(fileMappings);
		}
		if (reader == null) {
			throw new IllegalArgumentException("Couldn't get reader for mappings format text " + formatString + " and path " + fileMappings);
		}
		System.out.println("Reading mappings...");
		Map<MappingsOption, String> options = project.getEnigma().getProfile().getMappingsOptions(reader.getAllOptions());
		EntryTree<EntryMapping> mappings = reader.read(fileMappings, progress, options, project::getJarIndex);

		project.setMappings(mappings);
	}

	protected static File getWritableFile(String path) {
		if (path == null) {
			return null;
		}
		File file = new File(path).getAbsoluteFile();
		File dir = file.getParentFile();
		if (dir == null) {
			throw new IllegalArgumentException("Cannot write file: " + path);
		}
		// quick fix to avoid stupid stuff in Gradle code
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		return file;
	}

	protected static File getWritableFolder(String path) {
		if (path == null) {
			return null;
		}
		File dir = new File(path).getAbsoluteFile();
		if (!dir.exists()) {
			throw new IllegalArgumentException("Cannot write to folder: " + dir);
		}
		return dir;
	}

	protected static File getReadableFile(String path) {
		if (path == null) {
			return null;
		}
		File file = new File(path).getAbsoluteFile();
		if (!file.exists()) {
			throw new IllegalArgumentException("Cannot find file: " + file.getAbsolutePath());
		}
		return file;
	}

	protected static Path getReadablePath(String path) {
		if (path == null) {
			return null;
		}
		Path file = Paths.get(path).toAbsolutePath();
		if (!Files.exists(file)) {
			throw new IllegalArgumentException("Cannot find file: " + file.toString());
		}
		return file;
	}

	protected static String getArg(String[] args, int i, String name, boolean required) {
		if (i >= args.length) {
			if (required) {
				throw new IllegalArgumentException(name + " is required");
			} else {
				return null;
			}
		}
		return args[i];
	}

	public static class ConsoleProgressListener implements ProgressListener {

		private static final int ReportTime = 5000; // 5s

		private int totalWork;
		private long startTime;
		private long lastReportTime;

		@Override
		public void init(int totalWork, String title) {
			this.totalWork = totalWork;
			this.startTime = System.currentTimeMillis();
			this.lastReportTime = this.startTime;
			System.out.println(title);
		}

		@Override
		public void step(int numDone, String message) {
			long now = System.currentTimeMillis();
			boolean isLastUpdate = numDone == this.totalWork;
			boolean shouldReport = isLastUpdate || now - this.lastReportTime > ReportTime;

			if (shouldReport) {
				int percent = numDone * 100 / this.totalWork;
				System.out.println(String.format("\tProgress: %3d%%", percent));
				this.lastReportTime = now;
			}
			if (isLastUpdate) {
				double elapsedSeconds = (now - this.startTime) / 1000.0;
				System.out.println(String.format("Finished in %.1f seconds", elapsedSeconds));
			}
		}
	}
}
