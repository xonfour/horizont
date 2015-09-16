package db.orientdb.model;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * When converting database structures to JSON some fields have to be left out. This is definied here.
 *
 * @author Stefan Werner
 */
public class GsonOrientDBExclusionStrategy implements ExclusionStrategy {

	@Override
	public boolean shouldSkipClass(final Class<?> arg0) {
		return false;
	}

	@Override
	public boolean shouldSkipField(final FieldAttributes f) {
		return f.getName().equals("id") || f.getName().equals("version");
	}

}
