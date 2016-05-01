/**
 * 
 */
package com.pacbio.secondary.common.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.pacbio.secondary.common.core.CompatibilityMatrix.ColumnInput;
import com.pacbio.secondary.common.core.CompatibilityMatrix.Message;
import com.pacbio.secondary.common.core.CompatibilityMatrix.Permissible;
import com.pacbio.secondary.common.core.CompatibilityMatrix.RowInput;

/**
 * API for testing combinations of inputs against a matrix.
 * 
 * @author jmiller
 */
public class CompatibilityChecker {

	private static final String VERSION_FORMAT = "Versions must be of the form: v[MAJOR].[MINOR].[RELEASE], as in v1.3.0.";

	private CompatibilityMatrix matrix;

	private HashMap<String, Permissible> idMap;

	/**
	 * Construct with a matrix
	 * 
	 * @param matrix
	 */
	public CompatibilityChecker(CompatibilityMatrix matrix) {
		// validate(matrix);
		this.matrix = matrix;
		init();
	}


	/**
	 * Init the map of reusable permissible blocks.
	 */
	private void init() {
		if( matrix.getPermissibles() == null )
			return;
		idMap = new HashMap<String, Permissible>();
		for( Permissible p : matrix.getPermissibles() ) {
			idMap.put( p.getId(), p);
		}
	}

	/**
	 * Validate the format of the supplied versions.
	 * 
	 * @param versions
	 */
	protected static void validate(String... versions) {
		if (versions == null) {
			throw new IllegalArgumentException(
					"versions cannot be null or empty. " + VERSION_FORMAT);
		}
//		String regex = "v[0-9]\\.[0-9]\\.[0-9]";
		String regex = "v[0-9]+\\.[0-9]+\\.[0-9]+";
		for (String vers : versions) {
			if (!vers.matches(regex)) {
				throw new IllegalArgumentException(VERSION_FORMAT);
			}
		}
	}

	// API //
	public boolean isPermissible(List<String> versions) {
		if (versions == null || versions.isEmpty()) {
			throw new IllegalArgumentException(
					"versions cannot be null or empty. " + VERSION_FORMAT);
		}

		validate(versions.toArray(new String[] {}));

		for (Permissible p : getPermissibles(versions)) {
			if (!p.getValue())
				return false;
		}
		return true;
	}

	private List<Permissible> getPermissibles(List<String> versions) {

		List<Permissible> l = new ArrayList<Permissible>();

		List<ColumnInput> columns = getColumnInputs(versions);

		for (ColumnInput ci : columns) {

			List<RowInput> rows = getRowInputs(ci, versions);

			for (RowInput ri : rows) {
				List<Permissible> perms = ri.getPermissibles();
				
				for ( Permissible p : perms ) {
					if( p.getUseId() != null ) {
						if( idMap.get(p.getUseId()) == null )
							throw new RuntimeException( p.getUseId() + " does not correspond to a permissible with the same id.");
						l.add( idMap.get(p.getUseId()) );
					} else {
						l.add(p);
					}
				}
//				l.addAll(perms);

				// for (Permissible p : perms) {
				// if (!p.getValue())
				// return false;
				// }
			}
		}

		return l;
	}

	private List<RowInput> getRowInputs(ColumnInput ci, List<String> versions) {
		List<RowInput> l = new ArrayList<RowInput>();

		if (ci.getRowInputs() == null)
			return l;

		for (String v : versions) {
			for (RowInput ri : ci.getRowInputs()) {
				if (ri.getVersion().equals(v)) {
					l.add(ri);
				}
			}
		}

		return l;
	}

	/**
	 * Obtain a list of {@link ColumnInput}'s for the supplied versions. May
	 * return an empty list if no corresponding inputs exist under the runtime
	 * element.
	 * 
	 * @param versions
	 * @return list, never null
	 */
	private List<ColumnInput> getColumnInputs(List<String> versions) {
		List<ColumnInput> l = new ArrayList<ColumnInput>();
		if (matrix.getColumnInputs() == null)
			return l;

		for (String v : versions) {
			for (ColumnInput ci : matrix.getColumnInputs()) {
				if (ci.getVersion().equals(v)) {
					l.add(ci);
				}
			}
		}

		return l;
	}

	/**
	 * Get all messages associated with pairwise matrices. If any combination is
	 * not permissible, return only that message.
	 * 
	 * Also, if any combinations have "warn" level messages,return only a list
	 * of "warn" messages, since they are of greater importance.
	 * 
	 * @param versions
	 * @return messages
	 */
	public List<Message> getMessages(List<String> versions) {
		List<Message> messages = new ArrayList<Message>();
		
		List<Permissible> l = getPermissibles(versions);
		boolean hasWarn = false;
		for (Permissible p : l) {
			if (!p.getValue()) {
				messages.clear();
				messages.add(p.getMessage());
				//return single message associated with non-permissible
				return messages;
			}
			if (p.getMessage() != null) {
				if (p.getMessage().getLevel().equals("warn")) {
					hasWarn = true;
					break;
				}
				
				if( !messages.contains(p.getMessage()))
					messages.add(p.getMessage());
			}
		}
		
		if( hasWarn ) {
			//prune out non-warning messages
			messages.clear();
			for (Permissible p : l) {
				if (p.getMessage().getLevel().equals("warn")) {
					if( !messages.contains(p.getMessage()))
						messages.add(p.getMessage());
				}
			}
		}
		return messages;
	}
}
