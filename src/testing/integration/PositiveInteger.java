package testing.integration;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Used to check command line values for {@link RandomFileSystemSimulator}.
 *
 * @author Stefan Werner
 */
public class PositiveInteger implements IParameterValidator {

	@Override
	public void validate(final String name, final String value) throws ParameterException {
		final int i = Integer.parseInt(value);
		if (i < 0) {
			throw new ParameterException("ERROR: Parameter " + name + " is negative.");
		}
	}
}
