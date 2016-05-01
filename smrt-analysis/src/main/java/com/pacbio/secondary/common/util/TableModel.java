/**
 * 
 */
package com.pacbio.secondary.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic data structure for representing table-like data
 * @author jmiller
 */
public abstract class TableModel {

	protected boolean hasColumnNames;

	protected List<List<String>> dataLists;

	/**
	 * Column name at index
	 * 
	 * @param i
	 * @return
	 */
	public String getColumn(int i) {
		if (getColumnNames() == null)
			return null;
		return getColumnNames().get(i);
	}
	
	
	/**
	 * Get the column index for a given column name
	 * @param column
	 * @return
	 */
	public int getColumnIndex(String column) {
		if (getColumnNames() == null)
			return -1;
		int n = 0;
		for( String s : getColumnNames() ) {
			if(s.equals(column))
				return n;
			n++;
		}
		return -1;
	}


	/**
	 * Get a line of row data at index i. The column name row is not included.
	 * If {@link #setHasColumnNames(boolean)} has been called with true, you
	 * would still get your first row of data with i == 0.
	 * 
	 * @param i
	 * @return
	 */
	public List<String> getRow(int i) {
		if (noData())
			return null;
		int realRow = i;
		if (hasColumnNames)
			// the first row o' columns is not row data
			realRow = i + 1;
		return dataLists.get(realRow);
	}

	/**
	 * True, if the first line is a header line.
	 * False, if all data
	 * @param hasColumnNames
	 */
	public void setHasColumnNames(boolean hasColumnNames) {
		this.hasColumnNames = hasColumnNames;
	}

	/**
	 * Get a list of column names
	 * @return
	 */
	public List<String> getColumnNames() {
		if (noData())
			return null;
		if (!hasColumnNames)
			return null;

		return dataLists.get(0);
	}

	private boolean noData() {
		return this.dataLists == null || this.dataLists.isEmpty();
	}

	/**
	 * Transform the input data into a table model
	 */
	public void processData() {
		dataLists = createDataLists();
	}

	/**
	 * Parse the data into String lists. Up to the caller to ensure integrity.
	 * 
	 * @return
	 */
	protected abstract List<List<String>> createDataLists();

	
	/**
	 * Get data at the intersection
	 * @param row
	 * @param column
	 * @return
	 */
	public String getData(int row, int column) {
		if (noData())
			return null;
		List<String> dataRow = getRow(row);
		return dataRow.get(column);
	}

	
	/**
	 * Get column of data at the column index
	 * @param i	column index
	 * @return null if no data
	 */
	public List<String> getColumnData(int i) {
		if( noData() )
			return null;
		List<String> c = new ArrayList<String>();
		
		int start = 0;
		if( hasColumnNames )
			start = 1;
		
		for( int n = start; n < dataLists.size(); n++ ) {
			List<String> dataRow = dataLists.get(n);
			c.add(dataRow.get(i));
		}
		return c;
	}

	
	/**
	 * Get column of data mapping to the column
	 * @param column
	 * @return
	 */
	public List<String> getColumnData(String column) {
		int index = getColumnIndex(column);
		return getColumnData(index);
	}
}
