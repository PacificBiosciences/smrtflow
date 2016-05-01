/**
 * 
 */
package com.pacbio.secondary.common.sys.linux;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * Transform the df tabular output into a TablModel
 * @author jmiller
 */
public class DfTableModel extends TableModel {

	/** df produces exactly 6 columns of useable data, although there are 7 tokens in the header **/
	private static final int DF_COLUMN_NUM = 6;
	private String data;

	
	/**
	 * @param data	df output
	 */
	public DfTableModel(String data) {
		this.data = data;
	}

	@Override
	protected List<List<String>> createDataLists() {
		
		StringTokenizer lineTokenizer = new StringTokenizer(data, "\n");
		StringBuffer dataNoColumns = new StringBuffer();
		boolean first = true;
		while( lineTokenizer.hasMoreTokens() ) {
			if(first) {
				//Don't include the header line
				first = false;
				lineTokenizer.nextToken();
				continue;
			}
			dataNoColumns.append(lineTokenizer.nextToken()).append(" ");
		}
		
		List<List<String>> lines = new ArrayList<List<String>>();
		StringTokenizer st = new StringTokenizer(dataNoColumns.toString());
		int count = DF_COLUMN_NUM; //initialization
		
		List<String> line = null;
		while( st.hasMoreTokens() ) {
			if(count == DF_COLUMN_NUM ) {
				line = new ArrayList<String>();
				lines.add(line);
				count = 0;
			}
			line.add(st.nextToken());
			count++;
		}
		
		return lines;
		
	}

}
