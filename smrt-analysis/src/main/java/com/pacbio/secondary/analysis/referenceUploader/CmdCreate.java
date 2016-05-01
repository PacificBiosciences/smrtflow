/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.core.AbstractAnalysisConfig;
import org.apache.commons.lang.StringUtils;

import com.pacbio.secondary.analysis.referenceUploader.ProcessHelper.Executable;
import com.pacbio.secondary.analysis.referenceUploader.io.ByteObserver;
import com.pacbio.secondary.analysis.referenceUploader.io.ContigIndexWriter;
import com.pacbio.secondary.analysis.referenceUploader.io.FastaIndexWriter;
import com.pacbio.secondary.analysis.referenceUploader.io.MultiFileByteBroadcaster;
import com.pacbio.secondary.analysis.referenceUploader.io.MultiSeqFastaWriter;
import com.pacbio.secondary.analysis.referenceUploader.io.ObservableObserver;
import com.pacbio.secondary.analysis.referenceUploader.io.ReferenceInfoXmlWriter;
//import com.pacbio.secondary.analysis.referenceUploader.monitor.MultiFileBroadcasterMonitor;
import com.pacbio.secondary.common.model.Reference.IndexFileType;
import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceEntry.State;
import com.pacbio.secondary.common.model.ReferenceEntry.Type;
import com.pacbio.secondary.common.model.ReferenceIndex;
import com.pacbio.secondary.common.sys.ExecService;
import com.pacbio.secondary.common.sys.SysCommandExecutor;
import com.pacbio.secondary.common.sys.SysCommandResult;
import com.pacbio.secondary.common.util.Iso8601Date;

/**
 * @author jmiller
 */
public class CmdCreate extends CmdBase {

	private final static Logger LOGGER = Logger.getLogger(CmdCreate.class
			.getName());

	private String refName;

	private List<File> fastaFiles = new ArrayList<File>();

	private String refId;

	private long fastaFilesLength;

	private ReferenceEntry.Type refType = Type.Sample;

	private String organism;

	private String version;

	private String user;

	private String description;

	private String minFreeSpace;

	/** time, in minutes, that updates are posted **/
	private int progressFrequency = 1;

	private boolean skipUpdateIndexXml = false;

	private int lineLength = Constants.FASTA_LINE_LENGTH;

	public Map<Executable, String> postCreateProcessMap;

	private String jobId;

	private String ploidy;


	@Override
	public void run() throws Exception {

        System.out.println("Starting CmdCreate run.");
		validateRequiredInputs();
		checkDiskPre();
		init();

		MultiFileByteBroadcaster byteBroadcaster = new MultiFileByteBroadcaster(
				this.fastaFiles);

		ObservableObserver multiSeqFastaWriter = new MultiSeqFastaWriter(
				getMultiSeqPath(), lineLength);

        //MK. This will write the ReferenceInfo XML file
		addListenersToMultiSeqFastaWriter(multiSeqFastaWriter);

		byteBroadcaster.addObserver(multiSeqFastaWriter);
		//byteBroadcaster.addObserver(getProgressMonitor());

		try {
            System.out.println("Running parallel processes");
            //System.out.println(" process Map" + this.postCreateProcessMap.toString());
			byteBroadcaster.start();
			runPostCreateParallelProcesses();

		} catch (Exception e) {
			updateIndexXml(ReferenceEntry.State.Failed);
			throw e;
		}

		if (!skipUpdateIndexXml)
			updateIndexXml(ReferenceEntry.State.Active);
	}

	/**
	 * Runs in parallel all tasks that require an existing reference
	 * 
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private void runPostCreateParallelProcesses() throws InterruptedException,
			ExecutionException {
		if (this.postCreateProcessMap == null
				|| this.postCreateProcessMap.isEmpty()) {
            System.out.println("Skipping post process command. No post-process reference cmds found");
			LOGGER.fine("Not running any parallel processes after reference creation.");
			return;
		}

		ProcessHelper ph = new ProcessHelper(this.refRepos, this.refId,
				this.refName, this.postCreateProcessMap);

        System.out.println("Post create process Map");
        System.out.println(this.postCreateProcessMap);
        LOGGER.log(Level.INFO, "Process map " + this.postCreateProcessMap.toString());

        SysCommandExecutor[] parallel = ph.getSysCommandExecutors();

		StringBuffer sb = new StringBuffer(
				"Launching the following parallel processes:\n");
		for (SysCommandExecutor s : parallel) {
			sb.append("\n\t").append(s.getRunCommand());
		}
		LOGGER.info(sb.toString());

		ExecService es = new ExecService(getThreadPoolSize());
		List<Future<SysCommandResult>> results = es.execAll(parallel);

		ExecutionException exeException = null;
		for (Future<SysCommandResult> future : results) {
			if (future.isDone()) {
				try {

					SysCommandResult res = future.get();

					if (res.getExitCode() != 0)
						throw new Exception(
								"A post-reference-creation process failed with non-zero exit code: \n cmd = "
										+ res.getCommand() + "\nsys out = "
										+ res.getCmdOutput());
					else
						LOGGER.info(res.getCommand()
								+ " finished successfully!");

				} catch (Exception e) {
					// log all exceptions, throw one, which will trigger failed
					// state
					LOGGER.log(
							Level.SEVERE,
							"Encountered execution exception. This reference entry will be marked failed!",
							e);
					exeException = new ExecutionException(e);
				}
			}
		}
		if (exeException != null)
			throw exeException;
	}

	/**
	 * Get the lesser of: num processors or number of processes to run
	 * 
	 * @return in
	 */
	private int getThreadPoolSize() {
		if (Runtime.getRuntime().availableProcessors() < this.postCreateProcessMap
				.size())
			return Runtime.getRuntime().availableProcessors();
		return this.postCreateProcessMap.size();
	}

	/**
	 * Get a listener that reports back % complete of the byteBroadcaster.
	 * 
	 * @return
	 */
//	private ByteObserver getProgressMonitor() {
//		MultiFileBroadcasterMonitor monitor = new MultiFileBroadcasterMonitor();
//		monitor.setReferenceId(this.refId);
//		monitor.setProgressFrequency(this.progressFrequency);
//		monitor.setTotalBytes(this.fastaFilesLength);
//		monitor.start();
//		return monitor;
//	}

	/**
	 * Before even starting to create a reference, verify that enough space
	 * exists.
	 * 
	 * @throws Exception
	 */
	private void checkDiskPre() throws Exception {
		DiskSpaceChecker d = new DiskSpaceChecker(this.refRepos);
		d.doPreCreationCheck(this.fastaFilesLength, this.minFreeSpace);
	}

	private void updateIndexXml(State state) throws Exception {

		// avoid corrupting the repos if we run out of space
		DiskSpaceChecker d = new DiskSpaceChecker(this.refRepos);
		try {
			d.doPreIndexXmlUpdateCheck();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to update " + this.refRepos
					+ "/index.xml. There is not enough space.", e);
			return;
		}

		ReferenceIndex ri = null;
		try {
			ri = ReferenceIndex.openForEdit(this.refRepos);
			
			if( ri.getEntry(this.refId) != null ) {
				ri.deleteEntry(this.refId);
			}
			
			ReferenceEntry re = new ReferenceEntry();
			
			re.setUser(this.user);
			re.setVersion(this.version);
			re.setId(this.refId);
			re.setDirectory(this.refId);
			re.setState(state);
			re.setType(this.refType);
			re.setName(this.refName);
			re.setMetadata(ReposUtils.getReferenceInfoXmlPath(refId));
			re.setOrganism(organism);
			re.setJobId(this.jobId);
			
			ri.addEntry(re);
			
			ri.save();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE,
					"Failed to update " + this.refRepos + "/index.xml: "
							+ this.refId + " should be " + state.toString(), e);
			throw e;
		} finally {
			unlock(ri);
		}

		//If the state is failed, it's highly likely that the reference.info.xml doc
		//did not get written. In this case, updating the digest will fail, obscuring the real reason.
		if( state != State.Failed)
			updateIndexXmlDigest();

	}

	/**
	 * This is performed after the index.xml has been created. That's only
	 * because the digest was introduced in 1.2, and we'll need to upgrade
	 * existing references. This allows us to use the same code.
	 * 
	 * @throws Exception
	 */
	private void updateIndexXmlDigest() throws Exception {
		ReferenceChecksum rc = new ReferenceChecksum(refId, refRepos);
		rc.addChecksum();
	}

	/**
	 * Add observers to multiseq writer
	 * 
	 * @param oo
	 * @throws IOException
	 */
	private void addListenersToMultiSeqFastaWriter(ObservableObserver oo)
			throws IOException {
		ReferenceInfoXmlWriter rw = new ReferenceInfoXmlWriter(refId,
				ReposUtils.getReferenceDirPath(refRepos, refId));

        rw.setDescription(this.description);
		rw.getOrganism().setPloidy( this.ploidy );
		rw.getOrganism().setName( this.organism );
		rw.setMultiSeqFastaValue(ReposUtils.getMultiSeqFastaPath(refName));
		rw.setType(this.refType.toString());
		rw.addIndexFile(IndexFileType.Indexer,
				ReposUtils.getFastaIndexPath(refName));
		rw.setVersion(this.version);

        rw.addIndexFile(IndexFileType.Sa, ReposUtils.getSaIndexPath(refName));

//		if (runningGatkDict())
//			rw.addIndexFile(IndexFileType.GatkDict,
//					ReposUtils.getGatkDictPath(refName));

        // MK. Always run Samtools. This is a required file by quiver
        rw.addIndexFile(IndexFileType.SamIdx,
                ReposUtils.getSamIdxPath(refName));

        // MK. Always add this because it's always produced. Explicit add to reference.info.xml
        rw.addIndexFile(IndexFileType.FastaContigIndex,
                ReposUtils.getFastaContigIndex(refName));

        oo.addObserver(rw);
		FastaIndexWriter iw = new FastaIndexWriter(
				ReposUtils.getFastaIndexPath(refRepos, refId, refName), 0,
				this.fastaFilesLength);
		oo.addObserver(iw);

		addListenersToFastaIndexWriter(iw);
	}

//	private boolean runningGatkDict() {
//		if (this.postCreateProcessMap == null)
//			return false;
//		return postCreateProcessMap.containsKey(Executable.GatkDict);
//	}

	private boolean runningSawriter() {
		if (this.postCreateProcessMap == null)
			return false;
		return postCreateProcessMap.containsKey(Executable.SAW);
	}

	/**
	 * Add observers to Fasta index writer
	 * 
	 * @param oo
	 * @throws IOException
	 */
	private void addListenersToFastaIndexWriter(ObservableObserver oo)
			throws IOException {
		ContigIndexWriter iw = new ContigIndexWriter(
				ReposUtils.getContigIndexPath(refRepos, refId, refName));
		oo.addObserver(iw);
	}

	/**
	 * Get path to multiseq fasta file
	 * 
	 * @return
	 */
	private String getMultiSeqPath() {
		return ReposUtils.getMultiSeqFastaPath(this.refRepos, this.refId,
				refName);
	}

	/**
	 * Make any required directories
	 * 
	 * @throws IOException
	 *             if required dirs cannot be created
	 */
	private void init() throws IOException {
		File repos = new File(this.refRepos);
		if (!repos.exists()) {
			if (!repos.mkdirs()) {
				throw new IOException("Failed to make "
						+ repos.getAbsolutePath());
			}
		}
		if (this.refId == null) {
			this.refId = ReposUtils.nameToFileName(refName);
			if (new File(this.refRepos, refId).exists()) {
				refId = refId + "_"
						+ Iso8601Date.format(new Date()).replace(":", "_");
			}
		}
	}

	/**
	 * Check that all required inputs have been supplied to the cmd.
	 * 
	 * @throws Exception
	 */
	private void validateRequiredInputs() throws Exception {
		if (StringUtils.isBlank(this.refName))
			throw new IllegalArgumentException(
					"To create a reference, a name is required.");

		if (fastaFiles == null || fastaFiles.size() == 0)
			throw new IllegalArgumentException(
					"To create a reference, 1 or more input fasta files is required.");

		if (StringUtils.isBlank(refRepos))
			throw new IllegalArgumentException(
					"To create a reference, the repository location is required.");

		for (File f : this.fastaFiles) {
			if (!f.exists()) {
				throw new FileNotFoundException(
						"Aborting reference creation. This fasta file does not exist: "
								+ f);
			}
			long flen = f.length();
			if( flen == 0 )
				throw new IOException( "Illegal input. Fasta file cannot be empty: " + f.getAbsolutePath() );
			this.fastaFilesLength += flen;
		}
	}

	/**
	 * The name of the reference that will be created
	 * 
	 * @param refName
	 */
	public void setName(String refName) {
		this.refName = refName;
	}

	/**
	 * The fastaFiles to use as inputs to reference creation
	 * 
	 * @param fastaFiles
	 */
	public void setFastaFiles(String[] fastaFiles) {
		for (String ff : fastaFiles) {
			this.fastaFiles.add(new File(ff));
		}
	}

	// /**
	// * Set the full sawriter cmd string. If not null, gets executed when the
	// * reference entry is finished
	// *
	// * @param sawCmd
	// */
	// public void setExecutableSawCmd(String sawCmd) {
	// this.sawCmd = sawCmd;
	// }
	//
	// public void setExecutableGatkDictCmd(String absolutePath) {
	// // TODO Auto-generated method stub
	//
	// }

	/**
	 * Set the reference type. Default is sample.
	 * 
	 * @param type
	 */
	public void setRefType(Type type) {
		this.refType = type;
	}

	/**
	 * Optional organism type
	 * 
	 * @param organism
	 */
	public void setOrganism(String organism) {
		this.organism = organism;
	}

	/**
	 * ref descr
	 * 
	 * @param desc
	 */
	public void setDescription(String desc) {
		this.description = desc;
	}

	/**
	 * The person who created this? Or linux uname?
	 * 
	 * @param user
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Software version
	 * 
	 * @param version
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * Set the amount of free space that must exist in the repos. Else,
	 * reference creation is aborted.
	 * 
	 * @param min
	 */
	public void setFreeSpaceMin(String min) {
		if (min.endsWith("%") || min.endsWith("MB") || min.endsWith("GB")) {
			this.minFreeSpace = min;
		} else {
			throw new IllegalArgumentException(
					"min free space must be of the form N%, NMB or NGB");
		}
	}

	public void setProgressFrequency(int progressFrequency) {
		this.progressFrequency = progressFrequency;
	}

	/**
	 * Only the reference gets created, without updating index.xml
	 */
	public void skipUpdateIndexXml(boolean skip) {
		this.skipUpdateIndexXml = skip;
	}

	public void setFastaLineLength(int lineLength) {
		this.lineLength = lineLength;
	}

	/**
	 * Register an executable that should be run when reference creation is
	 * complete. It must have no dependencies other than the output of the core
	 * reference creation, and be able to run in parallel with other tasks.
	 * 
	 * @param exe
	 *            - the identifier of the executable, so that the cmd can be
	 *            built with appropriate options/args
	 * @param base
	 *            - the core command. Can be null, in which case no cmd is
	 *            registered.
	 */
	public void addPostCreationExecutable(Executable exe, String base) {
		if (base == null)
            //LOGGER.warning("Unable to add exe " + exe.toString());
			return;

		if (this.postCreateProcessMap == null)
			this.postCreateProcessMap = new HashMap<Executable, String>();
		this.postCreateProcessMap.put(exe, base);
	}

	/**
	 * During repository upgrade, we want to preserve the old id, instead of generating new one.
	 * @param jobId
	 */
	protected void setJobId(String jobId) {
		this.jobId = jobId;
	}

	/**
	 * During repository upgrade, we want to preserve the old id, instead of generating new one.
	 * @param refId
	 */
	protected void setRefId(String refId) {
		this.refId = refId;
	}

	
	/**
	 * Typically, this would "haploid" or "diploid", but many values are possible. This may also be null.
	 * @param ploidy
	 */
	public void setPloidy(String ploidy) {
		this.ploidy = ploidy;
	}

}
