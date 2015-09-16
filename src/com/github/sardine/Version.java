package com.github.sardine;

/**
 * Provides version information from the manifest.
 *
 * @author Jeff Schnitzer
 */
public final class Version {
	/**
	 * @return The <code>Implementation-Version</code> in the JAR manifest.
	 */
	public static String getImplementation() {
		final Package pkg = Version.class.getPackage();
		return (pkg == null) ? null : pkg.getImplementationVersion();
	}

	/**
	 * @return The <code>Specification-Version</code> in the JAR manifest.
	 */
	public static String getSpecification() {
		final Package pkg = Version.class.getPackage();
		return (pkg == null) ? null : pkg.getSpecificationVersion();
	}

	/**
	 * A simple main method that prints the version and exits
	 */
	public static void main(final String[] args) {
		System.out.println("Version: " + Version.getSpecification());
		System.out.println("Implementation: " + Version.getImplementation());
	}

	private Version() {
	}
}
