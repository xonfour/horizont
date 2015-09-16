package testing.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;

import framework.constants.Constants;

/**
 * Simulator to generate file system actions (randomly create, rename and remove file and folder structures with random data). The actions can be done on one or
 * more destination folders. Complexity and type can be specified. Also a reference directory may be created. The Simulator may either create a structur all at
 * once or slowly over time (for live testing).
 * <p>
 * <code>
 * Options:
 *   -fp, --folder-probability
 *      Probability for creating a folder rather than a file. 0 = files only, 100
 *      = folders only.
 *      Default: 25
 *   -h, /h, --help
 *      Display this help / usage information.
 *      Default: false
 *   -l, --live
 *      Live mode: Do not create a file system all at once but keep on simulating
 *      random writes.
 *      Default: false
 *   -me, --max-element-count
 *      Maximum number of elements (files/folders).
 *      Default: 200
 *   -ms, --max-file-size
 *      Maximum size of a file in KB.
 *      Default: 10240
 *   -m, --mirror-path
 *      Location where ALL writes are mirrored.
 * * -p, --path
 *      Base location(s) to use as base for simulated file system. If you specify
 *      more than one destination path for each write will be chosen by random.
 *      Default: []
 *   -t, --time-to-pause
 *      Time to pause between consecutive actions in milliseconds.
 *      Default: 1000
 * </code>
 * 
 * @author Stefan Werner
 */
public class RandomFileSystemSimulator {

	/**
	 * The enum ELEM_TYPE.
	 */
	private static enum ELEM_TYPE {
		FILE, FOLDER
	}

	/**
	 * The internal representation of an file system element.
	 */
	private class Element {

		private final List<Element> children;
		private final int depth;
		private final ELEM_TYPE elemType;
		private String name;
		private final Element parent;

		/**
		 * Instantiates a new element.
		 *
		 * @param elemType the element type
		 * @param name the name
		 * @param parent the parent element
		 */
		private Element(final ELEM_TYPE elemType, final String name, final Element parent) {
			this.elemType = elemType;
			this.name = name;
			this.parent = parent;
			if (parent == null) {
				this.depth = 0;
			} else {
				this.depth = parent.depth + 1;
			}
			if (this.elemType == ELEM_TYPE.FOLDER) {
				this.children = new ArrayList<RandomFileSystemSimulator.Element>();
			} else {
				this.children = null;
			}
		}
	}

	private static final int MAX_DEPTH = 16;
	private static final int MAX_FILENAME_LENGTH = 32;

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(final String[] args) {
		final RandomFileSystemSimulator sim = new RandomFileSystemSimulator(args);
		sim.run();
	}

	private final Element baseFolder = new Element(ELEM_TYPE.FOLDER, "", null);
	private final List<Path> basePaths = new ArrayList<Path>();
	@Parameter(
			names = { "-p", "--path" },
			description = "Base location(s) to use as base for simulated file system. If you specify more than one destination path for each write will be chosen by random.",
			required = true)
	private final List<String> basePathStrings = new ArrayList<String>();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
	private int elementCounter = 0;
	private final List<Element> files = new ArrayList<RandomFileSystemSimulator.Element>();
	@Parameter(names = { "-fp", "--folder-probability" },
			description = "probability for creating a folder rather than a file. 0 = files only, 100 = folders only.", validateWith = PositiveInteger.class)
	private final int folderProbability = 25;
	private final List<Element> folders = new ArrayList<RandomFileSystemSimulator.Element>();
	@Parameter(names = { "-h", "/h", "--help" }, description = "Display this help / usage information.", help = true)
	private boolean help;
	private final JCommander jCommander;

	private final Joiner joiner = Joiner.on(File.separator);
	@Parameter(names = { "-l", "--live" }, description = "Live mode: Do not create a file system all at once but keep on simulating random writes.")
	public boolean liveMode = false;
	@Parameter(names = { "-me", "--max-element-count" }, description = "Maximum number of elements (files/folders).", validateWith = PositiveInteger.class)
	private final int maxElements = 200;
	@Parameter(names = { "-ms", "--max-file-size" }, description = "Maximum size of a file in KB.", validateWith = PositiveInteger.class)
	private final int maxFileSize = 10240;
	private Path mirrorPath;
	@Parameter(names = { "-m", "--mirror-path" }, description = "Location where ALL writes are mirrored.")
	private final String mirrorPathString = null;
	@Parameter(names = { "-t", "--time-to-pause" }, description = "Time to pause between consecutive actions in milliseconds.",
			validateWith = PositiveInteger.class)
	private final int pause = 1000;
	private final Random rand = new Random(System.nanoTime());

	/**
	 * Instantiates a new random file system simulator.
	 *
	 * @param args the args
	 */
	public RandomFileSystemSimulator(final String[] args) {
		this.jCommander = new JCommander(this, args);
		if (this.help) {
			this.jCommander.usage();
			System.exit(0);
		}
		for (final String s : this.basePathStrings) {
			final Path p = Paths.get(s);
			if (Files.exists(p) && Files.isDirectory(p) && Files.isWritable(p)) {
				this.basePaths.add(p);
			} else {
				System.err.println("ERROR: Cannot access folder " + p.toAbsolutePath() + " for write");
				System.exit(1);
			}
		}
		if (this.mirrorPathString != null) {
			final Path p = Paths.get(this.mirrorPathString);
			if (Files.exists(p) && Files.isDirectory(p) && Files.isWritable(p)) {
				this.mirrorPath = p;
			} else {
				System.err.println("ERROR: Cannot access mirror folder " + p.toAbsolutePath() + " for write");
				System.exit(1);
			}
		}
	}

	/**
	 * Adds a random element.
	 *
	 * @return true, if successful
	 */
	private boolean addElement() {
		Path relPath = Paths.get("");
		Element parentElement = this.baseFolder;
		while (true) {
			final int i = this.rand.nextInt(this.folders.size() + 1);
			if (i >= this.folders.size()) {
				break;
			} else {
				final Element element = this.folders.get(i);
				if (element.depth >= RandomFileSystemSimulator.MAX_DEPTH) {
					continue;
				}
				relPath = getRelPath(element);
				parentElement = element;
				break;
			}
		}
		String name;
		Path parentPath = null;
		boolean parentFound = false;
		for (final Path basePath : this.basePaths) {
			parentPath = basePath.resolve(relPath);
			if (Files.exists(parentPath)) {
				parentFound = true;
				break;
			}
		}
		if (!parentFound) {
			return false;
		}
		Path destPath;
		while (true) {
			name = generateUniqueComponentId();
			destPath = parentPath.resolve(name);
			if (Files.notExists(destPath)) {
				break;
			}
		}
		if (shouldWorkOnFolder()) {
			try {
				System.out.println(getDateString() + " | CREATING FOLDER -> " + destPath.toString());
				Files.createDirectories(destPath);
			} catch (final IOException e) {
				e.printStackTrace();
				return false;
			}
			final Element element = new Element(ELEM_TYPE.FOLDER, name, parentElement);
			if (this.mirrorPath != null) {
				try {
					Files.createDirectories(this.mirrorPath.resolve(relPath).resolve(name));
				} catch (final IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			this.folders.add(element);
			parentElement.children.add(element);
		} else {
			final byte[] data = generateRandomData();
			System.out.println(getDateString() + " | CREATING FILE (" + data.length + " B) -> " + destPath.toString());
			if (!writeData(data, destPath)) {
				return false;
			}
			if (this.mirrorPath != null) {
				if (!writeData(data, this.mirrorPath.resolve(relPath).resolve(name))) {
					return false;
				}
			}
			final Element element = new Element(ELEM_TYPE.FILE, name, parentElement);
			this.files.add(element);
			parentElement.children.add(element);
		}
		this.elementCounter++;
		return true;
	}

	/**
	 * Chooses the next action.
	 *
	 * @return the int
	 */
	private int chooseAction() {
		if (this.elementCounter < (this.maxElements / 4)) {
			return 0;
		} else if (this.elementCounter == this.maxElements) {
			return this.rand.nextInt(2) + 1;
		} else {
			final int i = this.rand.nextInt(5) - 2;
			return Math.max(0, i);
		}
	}

	/**
	 * Cleans up.
	 *
	 * @param element the element
	 */
	private void cleanUp(final Element element) {
		if (element.children != null) {
			for (final Element child : element.children) {
				cleanUp(child);
			}
		}
		if (element.elemType == ELEM_TYPE.FILE) {
			this.files.remove(element);
		} else {
			this.folders.remove(element);
		}
		this.elementCounter--;
	}

	/**
	 * The real recursive delete operation.
	 *
	 * @param path the path
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void delete(final Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult postVisitDirectory(final Path folder, final IOException e) throws IOException {
				Files.delete(folder);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Deletes randomly an existing element.
	 *
	 * @return true, if successful
	 */
	private boolean deleteElement() {
		Element element;
		if (shouldWorkOnFolder()) {
			if (this.folders.isEmpty()) {
				return true;
			}
			element = this.folders.get(this.rand.nextInt(this.folders.size()));
		} else {
			if (this.files.isEmpty()) {
				return true;
			}
			element = this.files.get(this.rand.nextInt(this.files.size()));
		}
		final Path relPath = getRelPath(element);
		boolean pathFound = false;
		Path destPath = null;
		for (final Path basePath : this.basePaths) {
			destPath = basePath.resolve(relPath);
			if (Files.exists(destPath)) {
				pathFound = true;
				break;
			}
		}
		if (!pathFound) {
			return false;
		}
		System.out.println(getDateString() + " | DELETING " + element.elemType + " -> " + destPath.toString());
		try {
			if (element.elemType == ELEM_TYPE.FILE) {
				Files.delete(destPath);
			} else {
				delete(destPath);
			}
		} catch (final IOException e) {
			e.printStackTrace();
			return false;
		}
		if (this.mirrorPath != null) {
			try {
				if (element.elemType == ELEM_TYPE.FILE) {
					Files.delete(this.mirrorPath.resolve(relPath));
				} else {
					delete(this.mirrorPath.resolve(relPath));
				}
			} catch (final IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		cleanUp(element);
		if (element.parent != null) {
			element.parent.children.remove(element);
		}
		return true;
	}

	/**
	 * Generates some random amount of random data.
	 *
	 * @return the byte[]
	 */
	private byte[] generateRandomData() {
		final int size = this.rand.nextInt(this.maxFileSize * 1024);
		final byte[] buffer = new byte[size];
		this.rand.nextBytes(buffer);
		return buffer;
	}

	/**
	 * Generates a unique component ID (name).
	 *
	 * @return the string
	 */
	private String generateUniqueComponentId() {
		String id = "";
		for (int i = 0; i < (this.rand.nextInt(RandomFileSystemSimulator.MAX_FILENAME_LENGTH - 1) + 1); i++) {
			id += Constants.CORE___SESSION_ID_CHARS.charAt(this.rand.nextInt(Constants.CORE___SESSION_ID_CHARS.length()));
		}
		return id;
	}

	/**
	 * Gets a date string for console output.
	 *
	 * @return the date string
	 */
	private String getDateString() {
		return this.dateFormat.format(new Date(System.currentTimeMillis()));
	}

	/**
	 * Gets the relative path of an element.
	 *
	 * @param element the element
	 * @return the relative path
	 */
	private Path getRelPath(Element element) {
		final List<String> nameStrings = new ArrayList<String>();
		while (element.parent != null) {
			nameStrings.add(element.name);
			element = element.parent;
		}
		Collections.reverse(nameStrings);
		return Paths.get(this.joiner.join(nameStrings));
	}

	/**
	 * Modifies an existing element.
	 *
	 * @return true, if successful
	 */
	private boolean modifyElement() {
		if (shouldWorkOnFolder()) {
			if (this.folders.isEmpty()) {
				return true;
			}
			final Element element = this.folders.get(this.rand.nextInt(this.folders.size()));
			final Path relPath = getRelPath(element);
			boolean pathFound = false;
			Path destPath = null;
			for (final Path basePath : this.basePaths) {
				destPath = basePath.resolve(relPath);
				if (Files.exists(destPath)) {
					pathFound = true;
					break;
				}
			}
			if (!pathFound) {
				return false;
			}
			Path newDestPath;
			String newName;
			while (true) {
				newName = generateUniqueComponentId();
				newDestPath = destPath.getParent().resolve(newName);
				if (Files.notExists(newDestPath)) {
					break;
				}
			}
			System.out.println(getDateString() + " | RENAMING FOLDER (new name: " + newName + ") -> " + destPath.toString());
			try {
				Files.move(destPath, newDestPath);
			} catch (final IOException e) {
				e.printStackTrace();
				return false;
			}
			if (this.mirrorPath != null) {
				try {
					Files.move(this.mirrorPath.resolve(relPath), this.mirrorPath.resolve(relPath).getParent().resolve(newName));
				} catch (final IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			element.name = newName;
		} else {
			if (this.files.isEmpty()) {
				return true;
			}
			final Element element = this.files.get(this.rand.nextInt(this.files.size()));
			final byte[] data = generateRandomData();
			final Path relPath = getRelPath(element);
			boolean pathFound = false;
			Path destPath = null;
			for (final Path basePath : this.basePaths) {
				destPath = basePath.resolve(relPath);
				if (Files.exists(destPath)) {
					pathFound = true;
					break;
				}
			}
			if (!pathFound) {
				return false;
			}
			if (Files.notExists(destPath.getParent())) {
				return false;
			}
			System.out.println(getDateString() + " | OVERWRITING FILE DATA (new size: " + data.length + " B) -> " + destPath.toString());
			if (!writeData(data, destPath)) {
				return false;
			}
			if (this.mirrorPath != null) {
				if (!writeData(data, this.mirrorPath.resolve(relPath))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Runs the simulator.
	 */
	private void run() {
		if (!this.liveMode) {
			while ((this.elementCounter < this.maxElements) && !Thread.currentThread().isInterrupted()) {
				Collections.shuffle(this.basePaths, this.rand);
				if (!addElement()) {
					System.err.println("ERROR: Unable to find suitable base path to add an element");
					break;
				}
				if (this.pause > 0) {
					try {
						Thread.sleep(this.pause);
					} catch (final InterruptedException e) {
						break;
					}
				}
			}
		} else {
			while (!Thread.currentThread().isInterrupted()) {
				Collections.shuffle(this.basePaths, this.rand);
				boolean success = false;
				final int action = chooseAction();
				switch (action) {
				case 0:
					success = addElement();
					break;
				case 1:
					success = modifyElement();
					break;
				case 2:
					success = deleteElement();
					break;
				}
				if (!success) {
					System.err.println("ERROR: Unable to find suitable base path for action");
					break;
				}
				if (this.pause > 0) {
					try {
						Thread.sleep(this.pause);
					} catch (final InterruptedException e) {
						break;
					}
				}
			}
		}
	}

	/**
	 * Randomly selects if the next action should work on folders or files (based on given probability).
	 *
	 * @return true, if successful
	 */
	private boolean shouldWorkOnFolder() {
		return this.rand.nextInt(100) < this.folderProbability;
	}

	/**
	 * Writes data to file.
	 *
	 * @param data the data
	 * @param filePath the file path
	 * @return true, if successful
	 */
	private boolean writeData(final byte[] data, final Path filePath) {
		try {
			Files.write(filePath, data);
		} catch (final IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
