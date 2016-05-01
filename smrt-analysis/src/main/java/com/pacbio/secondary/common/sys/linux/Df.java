/**
 * 
 */
package com.pacbio.secondary.common.sys.linux;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.pacbio.secondary.common.sys.SysCommandExecutor;

/**
 * The command should be obtained from {@link LinuxCmdFactory} Unix df command.
 * Typical usage:
 * 
 * <pre>
 * Df df = LinuxCmdFactory.instance().getDf();
 * df.setFiles(&quot;/opt&quot;);
 * df.setOptions(&quot;-h&quot;);
 * df.exec();
 * String used = df.getUsed(); // this will give you something like 43GB
 * </pre>
 * 
 * @author jmiller
 * 
 */
public class Df implements IDf {

	private final static Logger LOGGER = Logger
			.getLogger(Df.class.getName());

	private String[] options;
	private String[] files;

	private TableModel tableModel;

	/**
	 * Use {@link LinuxCmdFactory} to get an instance
	 */
	protected Df() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getFileSystems()
	 */
	@Override
	public List<String> getFileSystems() {
		return tableModel.getColumnData(0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getSize(java.lang.String)
	 */
	@Override
	public String getSize(String filesystem) {
		int rowNum = getRowNum(filesystem);
		return tableModel.getData(rowNum, 1);
	}

	private int getRowNum(String filesystem) {
		List<String> fs = getFileSystems();
		for (int n = 0; n < fs.size(); n++) {
			if (filesystem.equals(fs.get(n)))
				return n;
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getUsed(java.lang.String)
	 */
	@Override
	public String getUsed(String filesystem) {
		return tableModel.getData(this.getRowNum(filesystem), 2);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getAvailable(java.lang.String)
	 */
	@Override
	public String getAvailable(String filesystem) {
		return tableModel.getData(this.getRowNum(filesystem), 3);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getUse(java.lang.String)
	 */
	@Override
	public String getUse(String filesystem) {
		return tableModel.getData(this.getRowNum(filesystem), 4);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getMountedOn(java.lang.String)
	 */
	@Override
	public String getMountedOn(String filesystem) {
		return tableModel.getData(this.getRowNum(filesystem), 5);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#setFiles
	 */
	public void setFiles(String... files) {
		this.files = files;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#getFileSystem(java.lang.String)
	 */
	@Override
	public String getFileSystem(String file) {
		int fileIndex = getFileIndex(file);
		for (int n = 0; n < tableModel.getColumnData(0).size(); n++) {
			if (n == fileIndex)
				return tableModel.getColumnData(0).get(n);
		}
		return null;
	}

	private int getFileIndex(String file) {
		for (int n = 0; n < files.length; n++) {
			if (file.equals(files[n]))
				return n;
		}
		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#setOptions
	 */
	public void setOptions(String... options) {
		this.options = options;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.common.sys.IDf#exec()
	 */
	@Override
	public void exec() throws Exception {

		SysCommandExecutor se = new SysCommandExecutor(getCommandArgList());
		se.setMergeStdOutErr(false);
		se.runCommand();

		if (se.getCommandError() != null && !"".equals(se.getCommandError()))
			throw new Exception(se.getCommandError());

		LOGGER.fine(se.getCommandOutput()  );

		tableModel = new DfTableModel(se.getCommandOutput());

		// we know the df command has column names
		tableModel.setHasColumnNames(false);

		tableModel.processData();

	}

	/**
	 * Consolidate the pieces in one list
	 * 
	 * @return
	 */
	private List<String> getCommandArgList() {
		List<String> l = new ArrayList<String>();
		l.add("df");
		if (options != null) {
			for (String o : options)
				l.add(o);
		}
		if (files != null) {
			for (String f : files)
				l.add(f);
		}

		return l;
	}

}
