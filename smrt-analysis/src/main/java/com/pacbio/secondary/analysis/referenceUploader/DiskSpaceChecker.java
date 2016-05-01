/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.sys.linux.IDf;
import com.pacbio.secondary.common.sys.linux.LinuxCmdFactory;

/**
 * Logic responsible for checking that adequate space exists on the repository
 * partition before executing a creation or update
 * 
 * @author jmiller
 * 
 */
public class DiskSpaceChecker {

	private final static Logger LOGGER = Logger
			.getLogger(DiskSpaceChecker.class.getName());

	private String refRepos;

	/**
	 * 
	 * @param fastaFilesLength
	 *            num bytes of source fasta input files
	 * @param refRepos
	 *            path to reference repository
	 * @param minFreeSpace
	 *            user-defined buffer space that must exist before a reference
	 *            is created
	 */
	public DiskSpaceChecker(
			String refRepos)
	{

		this.refRepos = refRepos;
	}

	/**
	 * Throw an exception if space criteria are not met before trying to create
	 * a reference.
	 * 
	 * @throws Exception
	 */
	public void doPreCreationCheck(long fastaFilesLength, String minFreeSpace)
			throws Exception {
		long reposFreeBytesKb = 0;
		try {
			reposFreeBytesKb = getFreeSpaceInRespos();
		} catch (Exception e) {
			LOGGER.log(
					Level.WARNING,
					"Could not determine the free space available in the repository. Proceeding anyway.",
					e);
			return;
		}

		// this is the bare minimum - if we cannot write out the multi-fasta, we
		// cannot go any further
		long fastaFilesLengthKb = fastaFilesLength / 1000;
		if (fastaFilesLengthKb >= reposFreeBytesKb)
			throw new Exception(
					"Not enough free space in the repository. Required (kb): "
							+ fastaFilesLengthKb + ". Free (kb): "
							+ reposFreeBytesKb);

		// now, did the user define a min free space threshold?
		if (minFreeSpace == null)
			// not set, we're done
			return;

		if (minFreeSpace.endsWith("%")) {
			checkSpaceAsPercentage(minFreeSpace);
			return;
		}

		long minFreeSpaceKb = getMinFreeSpaceKb(minFreeSpace);
		if (reposFreeBytesKb < minFreeSpaceKb) {
			throw new Exception(
					"The amount of free space in the repository is "
							+ reposFreeBytesKb + "kb. The min required is "
							+ minFreeSpaceKb + "kb");
		}

	}

	private long getMinFreeSpaceKb(String minFreeSpace) {
		if (minFreeSpace.endsWith("MB")) {
			long base = Long.parseLong(minFreeSpace.replace("MB", ""));
			return base * 1000;
		}
		if (minFreeSpace.endsWith("GB")) {
			long base = Long.parseLong(minFreeSpace.replace("GB", ""));
			return base * 1000 * 1000;
		}
		return 0;
	}

	private void checkSpaceAsPercentage(String minFreeSpace) throws Exception {
		IDf df = null;
		try {
			df = LinuxCmdFactory.instance().getDf();
		} catch (Exception e) {
			LOGGER.log(
					Level.WARNING,
					"Could not determine disk use in the repository. Proceeding anyway.",
					e);
			return;
		}

		df.setFiles(this.refRepos);
		df.setOptions("-BkB");
                if (System.getProperty("os.name").contains("OS X")){ df.setOptions("-Pk"); }
		String fs = df.getFileSystem(this.refRepos);
		int use = Integer.parseInt(df.getUse(fs).replace("%", ""));
		int free = 100 - use;
		int min = Integer.parseInt(minFreeSpace.replace("%", ""));

		if (free < min)
			throw new Exception(
					"The amount of free space in the repository is " + free
							+ "%. The min required is " + minFreeSpace);

	}

	/**
	 * in kb blocks
	 * 
	 * @return
	 * @throws Exception
	 */
	private long getFreeSpaceInRespos() throws Exception {
		IDf df = LinuxCmdFactory.instance().getDf();
		df.setFiles(this.refRepos);
		df.setOptions("-BkB");
                if (System.getProperty("os.name").contains("OS X")) { 
			df.setOptions("-Pk"); 
		} else {
			df.setOptions("-P");
		}
		
		df.exec();
		
		String fs = df.getFileSystem(this.refRepos);
		String avail = df.getAvailable(fs).replace("kB", "");
		return Long.parseLong(avail);
	}

	/**
	 * Avoid trying to update index.xml if there's not enough space to write a
	 * little bitty xml file.
	 * 
	 * @throws Exception
	 */
	public void doPreIndexXmlUpdateCheck() throws Exception {
		long free = getFreeSpaceInRespos();

		File indexXml = new File(new File(this.refRepos), "index.xml");
		if (!indexXml.exists()) {

			// 2000kb enough to write an index xml
			if (free >= 2000)
				return;

			throw new Exception("Only " + free
					+ "kb available in repos. Not updating index.xml!");
		}

		long indexKb = indexXml.length() / 1000;
		if (free <= indexKb) {
			throw new Exception("Only " + free
					+ "kb available in repos. index.xml size = " + indexKb
					+ "kb. Not updating index.xml!");
		}

	}

}
