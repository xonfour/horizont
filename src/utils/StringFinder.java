package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used to search for text Strings to translate in source code recursively, for some filtering and fpr adding found Strings to Property files.
 * <p>
 * USAGE: Call with parameters: root folder to start search at (resource file 1, resource file 2 ...).<br>
 * You can specify multiple resource files. StringFinder will do the following:<br>
 * 1: It will create a backup of the old state (.old).<br>
 * 2: It will add missing Strings to EVERY file<br>
 * 3: It will remove Strings in these files that could not be found in the code and save to another location (.removed).
 *
 * @author Stefan Werner
 */
public class StringFinder {

	private static final String[] ELEMENTS_TO_IGNORE = { "cell \\d+", "flowx", "flowy", "growx", "growy", "\\.png", "[,\\[]grow[,\\]]", "\\[\\]", "\\n" }; // used
	private static Map<String, Properties> existingResources = new HashMap<String, Properties>();
	private static final String[] FILES_TO_IGNORE = { "HumanReadableIdGenerationHelper.java" };
	private static final String[] LINES_TO_IGNORE = { "MigLayout", "System.out.print", "System.err.print" }; // used to form regex -> escape appropriately
	private static final int MIN_STRING_LENGTH = 2;
	private static Map<String, Properties> newResources = new HashMap<String, Properties>();
	private static final String OLD_FILE_EXT = ".old";
	private static final Pattern PATTERN = Pattern.compile(StringFinder.PREFIX + "(.*?)" + StringFinder.SUFFIX);
	// private static final String PREFIX = "\\.getLocalizedString\\(\"";
	// private static final String SUFFIX = "\"\\)";
	private static final String PREFIX = "\"";

	private static final String REMOVED_FILE_EXT = ".removed";
	private static final String SUFFIX = "\"";

	/**
	 * The main method.
	 * <p>
	 * Usage: <root folder> (<resource file 1> ... )
	 *
	 * @param args the arguments
	 * @throws IOException if an I/O exception has occurred.
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("ERROR: MISSING PARAMETER(S)\nUsage: <root folder to start recursive search> (<resource file 1> ...)");
			System.exit(1);
		}
		final Path rootPath = Paths.get(args[0]);
		if (!Files.isDirectory(rootPath)) {
			System.err.println("ERROR: invalid root folder");
			System.exit(1);
		}
		System.out.println("Searching in " + rootPath.toAbsolutePath() + " ...");

		for (int i = 1; i < args.length; i++) {
			final Path rPath = Paths.get(args[i]);
			final Properties props = new Properties();
			if (Files.isRegularFile(rPath) && Files.isReadable(rPath)) {
				final FileReader fr = new FileReader(args[i]);
				props.load(fr);
				fr.close();
			}
			StringFinder.existingResources.put(args[i], props);
			StringFinder.newResources.put(args[i], new Properties());
		}

		Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path arg0, final BasicFileAttributes arg1) throws IOException {
				if (arg0.toString().endsWith(".java")) {
					for (final String ign : StringFinder.FILES_TO_IGNORE) {
						if (arg0.toString().contains(ign)) {
							return FileVisitResult.CONTINUE;
						}
					}
					final BufferedReader br = new BufferedReader(new FileReader(arg0.toFile()));
					String line;
					while ((line = br.readLine()) != null) {
						boolean lSkip = false;
						for (final String is : StringFinder.LINES_TO_IGNORE) {
							if (line.matches(".*" + is + ".*")) {
								lSkip = true;
								break;
							}
						}
						if (lSkip) {
							continue;
						}
						final Matcher matcher = StringFinder.PATTERN.matcher(line);
						while (matcher.find()) {
							final String k = matcher.group(1);
							boolean eSkip = false;
							for (final String is : StringFinder.ELEMENTS_TO_IGNORE) {
								if (k.matches(".*" + is + ".*")) {
									eSkip = true;
									break;
								}
							}
							if (eSkip || (k.length() < StringFinder.MIN_STRING_LENGTH) || k.matches("\\P{IsAlphabetic}*")) {
								continue;
							}
							for (final String s : StringFinder.existingResources.keySet()) {
								final Properties eProps = StringFinder.existingResources.get(s);
								final Properties nProps = StringFinder.newResources.get(s);
								final Object foundObj = eProps.remove(k);
								if (foundObj != null) {
									nProps.put(k, foundObj);
								} else {
									nProps.put(k, "#" + k + "#");
								}
							}
						}
					}
					br.close();
				}
				return FileVisitResult.CONTINUE;
			}
		});

		for (final String s : StringFinder.existingResources.keySet()) {
			final Path rPath = Paths.get(s);
			final Path oRPath = Paths.get(s + StringFinder.OLD_FILE_EXT);
			if (Files.notExists(rPath) || Files.move(rPath, oRPath, StandardCopyOption.REPLACE_EXISTING).equals(oRPath)) {
				Properties props;
				FileWriter fw;

				props = StringFinder.existingResources.get(s);
				if (!props.isEmpty()) {
					fw = new FileWriter(s + StringFinder.REMOVED_FILE_EXT, false);
					props.store(fw, "MISSING ELEMENTS - ROOT PATH: " + rootPath);
					fw.close();
				}

				props = StringFinder.newResources.get(s);
				fw = new FileWriter(s, false);
				props.store(fw, "FOUND ELEMENTS - ROOT PATH: " + rootPath);
				fw.close();
			}
		}
	}
}
