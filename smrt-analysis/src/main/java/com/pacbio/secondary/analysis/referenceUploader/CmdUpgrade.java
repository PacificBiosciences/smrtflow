/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.pacbio.secondary.analysis.referenceUploader.ProcessHelper.Executable;
import com.pacbio.secondary.common.core.AnalysisContext;
import com.pacbio.secondary.common.model.Reference.IndexFile;
import com.pacbio.secondary.common.model.Reference.IndexFileType;
import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceEntry.Type;
import com.pacbio.secondary.common.model.ReferenceInfo;
import com.pacbio.secondary.common.sys.SysCommandExecutor;

/**
 * The logic of this class changes between releases. It does what is required to
 * get an individual reference upgraded from one version to the next.
 * 
 * For the 1.3.3 --> 1.4 Upgrade: This upgrade is fairly major. In 1.4, we
 * changed how the fasta header is written (no pacBio id), how the contig name
 * is generated, and how the refId is generated. The sa file, however, is still
 * usable.
 * 
 * 
 * The upgrade strategy: Regenerate the references, using the existing fasta as
 * input. Prior to using the existing fasta, the pesky pacbio ids need to be
 * purged from the header!! Then, if the sa file existed in the old ref, mv it
 * into the new. But, the reference.info.xml file won't reflect this. So, this
 * has to be edited, post facto.
 * 
 * But even then, it needs to be named correctly when it's moved. In 1.4 we're
 * replacing all symbols with underscores when creating the id. So, an sa file
 * that was named "foo+bar.fasta.sa" will be named "foo_bar.fasta.sa". This
 * holds true for all files.
 * 
 * @author jmiller
 */
public class CmdUpgrade extends CmdBase {

	private final static Logger LOGGER = Logger.getLogger(CmdUpgrade.class
			.getName());

	private Map<String, String> argMap = new HashMap<String, String>();

	private String refId;

	private ReferenceInfo info;

	private String name;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.analysis.referenceUploader.CmdBase#run()
	 */
	@Override
	public void run() throws Exception {
		File refDir = new File(ReposUtils.getReferenceDirPath(refRepos,
				this.refId));
		info = ReferenceInfo.load(refDir);

		createReferenceCopy();
		removePacBioIdFromFasta();

		CmdCreate cc = new CmdCreate();
		cc.setDescription(info.getReference().getDescription());
		cc.setFastaFiles(new String[] { getFastaOriginal().getPath() });
		cc.setName(this.name);
		cc.setReferenceRepos(this.refRepos);
		cc.setRefType(getType(info.getReference().getType()));
		cc.setVersion(Help.instance().getVersion());

		// this old refId my have symbols like '-'. So, convert to 1.4.4.
		// compliant id.
//		String refId_1_4 = ReposUtils.nameToFileName(this.refId);
		
		String refId_1_4 = this.refId;
		
		cc.setRefId(refId_1_4);

//		cc.addPostCreationExecutable(Executable.GatkDict, new File(
//				AnalysisContext.getInstance().getAnalysisRoot(),
//				"analysis/bin/createSequenceDictionary").getPath());
		cc.addPostCreationExecutable(Executable.SamIdx, new File(
				AnalysisContext.getInstance().getAnalysisRoot(),
				"analysis/bin/samtools").getPath() + " faidx");
		
		//bug 21768 - this takes too long and consumes too much space
//		cc.addPostCreationExecutable(Executable.GmapBuild, new File(
//				AnalysisContext.getInstance().getAnalysisRoot(),
//				"analysis/bin/gmap_build").getPath() + " --db=gmap_db");
		

		cc.run();

		File oldSa = getSaOriginal();

		if (oldSa != null) {
			File sa = new File(ReposUtils.getSaIndexPath(this.refRepos,
					refId_1_4, this.name));

			SysCommandExecutor e = new SysCommandExecutor("mv",
					oldSa.getPath(), sa.getPath());
			if (e.runCommand() != 0) {
				throw new RuntimeException(e.getRunCommand() + "\n"
						+ e.getCommandOutput());
			}


			File refdir = new File(ReposUtils.getReferenceDirPath(
					this.refRepos, refId_1_4));
			ReferenceInfo ri = ReferenceInfo.load(refdir);
			IndexFile i = new IndexFile();
			i.setType(IndexFileType.Sa.toString());
			i.setValue(ReposUtils.getSaIndexPath(name));
			ri.getReference().addIndexFile(i);
			ri.save(refdir);
		}

		// this delete won't get executed if an exception is thrown
		try {
			FileUtils.forceDelete(this.getRefDirOrig());
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "failed delete old reference "
					+ this.getRefDirOrig().getAbsolutePath(), e);
		}

	}

	
	
	/**
	 * Get the original fasta file
	 * 
	 * @return file
	 */
	private File getSaOriginal() {
		IndexFile i = info.getReference().getIndexFile(IndexFileType.Sa);
		if (i == null )
			return null;
		return new File(getRefDirOrig(), i.getValue() );
	}
	
	
	/**
	 * Get the original fasta file
	 * 
	 * @return file
	 */
	private File getFastaOriginal() {
		return new File(getRefDirOrig(), info.getReference().getFile()
				.getValue());
	}

	/**
	 * Get {@link ReferenceEntry.Type}
	 * 
	 * @param type
	 * @return
	 */
	private Type getType(String type) {
		for (Type t : ReferenceEntry.Type.values()) {
			if (t.toString().equals(type))
				return t;
		}
		return null;
	}

	//sed -i.bak 's,ref[0-9]\{6\}|,,g'
	private void removePacBioIdFromFasta() throws Exception {
		
		File origFasta = getFasta(getRefDirOrig());
		
		String sed = "sed -i.bak 's,ref[0-9]\\{6\\}|,,g' "
				+ origFasta.getPath();
		SysCommandExecutor e = new SysCommandExecutor("bash", "-c", sed);
		if (e.runCommand() != 0) {

			throw new RuntimeException(e.getRunCommand() + "\n"
					+ e.getCommandOutput());
		}
	}

	private File getFasta(File refDirOrig) throws FileNotFoundException {
		FileFilter ff = new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				if( pathname.getName().endsWith(".fasta"))
					return true;
				return false;
			}
		};
		
		File seqDir = new File(refDirOrig, "sequence");
		
		File[] files = seqDir.listFiles(ff);
		if( files.length == 0 ) {
			throw new FileNotFoundException( "No fasta file found under " + seqDir );
		}
		
		return files[0];
	}



	/**
	 * Create back up of the reference dir, return path to fasta file in
	 * original dir, to be used as fasta input
	 * 
	 * @param info
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private void createReferenceCopy(/* ReferenceInfo info */)
			throws IOException, Exception {

		File refDir = new File(ReposUtils.getReferenceDirPath(refRepos,
				info.getId()));

		File refDirOrig = getRefDirOrig();

		SysCommandExecutor exe = new SysCommandExecutor("mv", refDir.getPath(),
				refDirOrig.getPath());
		exe.setRunAsync(false);
		if (exe.runCommand() != 0) {
			throw new IOException("Could not run: " + exe.getRunCommand()
					+ "\nSys err:" + exe.getCommandError());
		}
	}

	/**
	 * Get the renamed dir
	 * 
	 * @return file
	 */
	private File getRefDirOrig() {
		return new File(ReposUtils.getReferenceDirPath(refRepos, info.getId()
				+ "_original"));
	}

	/**
	 * Parse the argbag into a map.
	 * 
	 * @param argbag
	 *            - of form foo=bar;baz=foo;
	 */
	public void setArgbag(String argbag) {
		if (argbag == null)
			return;
		try {
			parseArgs(argbag);
			// getRefId();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "arg bag parse failure", e);
			throw new RuntimeException(e);
		}

	}

	private void parseArgs(String argbag) {
		StringTokenizer st = new StringTokenizer(argbag, ";");
		while (st.hasMoreElements()) {
			StringTokenizer st2 = new StringTokenizer(st.nextToken(), "=");
			argMap.put(st2.nextToken(), st2.nextToken());
		}
	}

	public void setRefId(String refId) {
		this.refId = refId;
	}

	/**
	 * Theoretically, it's possible to get the refName out of index.xml.
	 * However, we generally delete and recreate this during the upgrade.
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

}
