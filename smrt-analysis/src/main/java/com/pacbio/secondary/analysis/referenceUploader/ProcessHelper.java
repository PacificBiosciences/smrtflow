/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.sys.IProcessLogger;
import com.pacbio.secondary.common.sys.SysCommandExecutor;

/**
 * @author jmiller
 * 
 */
public class ProcessHelper {

	private final static Logger LOGGER = Logger.getLogger(ProcessHelper.class
			.getName());

	public enum Executable {
		SAW, /*GatkDict,*/ SamIdx, GmapBuild
	}

	private String refRepos;
	private String refId;
	private String refName;
	private Map<Executable, String> postCreateProcessMap;

	/**
	 * Create a process helper with tokens required to build up args for
	 * executables
	 * 
	 * @param refRepos
	 * @param refId
	 * @param refName
	 */
	public ProcessHelper(String refRepos, String refId, String refName,
			Map<Executable, String> postCreateProcessMap) {
		this.refRepos = refRepos;
		this.refId = refId;
		this.refName = refName;
		this.postCreateProcessMap = postCreateProcessMap;
	}

	/**
	 * Get an array of {@link SysCommandExecutor}, with commands built up
	 * appropriately based on the executable type
	 * 
	 * @param postCreateProcessMap
	 * @return executor array
	 */
	public SysCommandExecutor[] getSysCommandExecutors() {

		List<SysCommandExecutor> l = new ArrayList<SysCommandExecutor>();
		for (Executable e : postCreateProcessMap.keySet()) {
			if (e.equals(Executable.SAW)) {
				l.add(getSawSysCmdExecutor());
//			} else if (e.equals(Executable.GatkDict)) {
//				l.add(getGatkDictSysCmdExecutor());
				
			} else if (e.equals(Executable.SamIdx)) {
				l.add(getSamIdxSysCmdExecutor());
				
			} else if (e.equals(Executable.GmapBuild)) {
				l.add(getGmapBuildSysCmdExecutor());
				
			} else {
				throw new RuntimeException(
						"Unimplemented logic? Unable to produce cmd arg list based on this core cmd: "
								+ postCreateProcessMap.get(e));
			}
		}

		return l.toArray(new SysCommandExecutor[] {});
	}

	protected SysCommandExecutor getSamIdxSysCmdExecutor() {
		List<String> args = getSamIdxCmdList();
		return getSysCmdExecutor(args);
	}

//	protected SysCommandExecutor getGatkDictSysCmdExecutor() {
//		List<String> args = getGatkDictCmdList();
//		return getSysCmdExecutor(args);
//	}
	
	protected SysCommandExecutor getGmapBuildSysCmdExecutor() {
		List<String> args = getGmapBuildCmdList();
		return getSysCmdExecutor(args);
	}

	/**
	 * Get a {@link SysCommandExecutor} ready to run Sawriter
	 * 
	 * @return sysCommandExecutor
	 */
	protected SysCommandExecutor getSawSysCmdExecutor() {
		List<String> args = getSawCmdList();
		return getSysCmdExecutor(args);
	}

	/**
	 * Get an executor
	 * 
	 * @param args
	 *            - from which cmd is created
	 * @return executor
	 */
	private SysCommandExecutor getSysCmdExecutor(List<String> args) {
		SysCommandExecutor s = new SysCommandExecutor(args);
		// the log will capture output, so don't consume memory with this string
		s.setCaptureStdOutput(false);
		// cannot be async or we get no exit code!
		s.setRunAsync(false);
		s.setOutputLogger(new IProcessLogger() {

			@Override
			synchronized public void log(String line, Throwable t) {
				LOGGER.log(Level.WARNING, line, t);

			}

			@Override
			synchronized public void log(String line) {
				LOGGER.log(Level.INFO, line);

			}
		});
		// return s.runCommand();
		return s;
	}

	/**
	 * Munge the cmd string into a string list, acceptable to
	 * {@link ProcessBuilder}
	 * 
	 * @return
	 */
	protected List<String> getSawCmdList() {
		List<String> l = new ArrayList<String>();
		boolean first = true;
		StringTokenizer st = new StringTokenizer(
				this.postCreateProcessMap.get(Executable.SAW));
		while (st.hasMoreTokens()) {
			String s = st.nextToken();
			if (first) {
				l.add(s);
				// these args need to be interjected
				l.add(getSaIndexPath());
				l.add(ReposUtils.getMultiSeqFastaPath(this.refRepos,
						this.refId, this.refName));
			} else {
				l.add(s);
			}
			first = false;
		}
		return l;
	}

	private String getSaIndexPath() {
		return ReposUtils.getSaIndexPath(this.refRepos, this.refId,
				this.refName);
	}

//	/**
//	 * @return the cmd to run the GATK dict process
//	 */
//	public List<String> getGatkDictCmdList() {
//		List<String> l = new ArrayList<String>();
//		l.add(this.postCreateProcessMap.get(Executable.GatkDict));
//		l.add("R="
//				+ ReposUtils.getMultiSeqFastaPath(this.refRepos, this.refId,
//						this.refName));
//
//		File refDir = new File(ReposUtils.getSequenceDirPath(refRepos, refId));
//
//		l.add("O="
//				+ new File(refDir, ReposUtils.nameToFileName(refName) + ".dict"));
//		return l;
//	}

	
	/**
	 * @return the cmd to run the samtools index process
	 */
	public List<String> getSamIdxCmdList() {
		List<String> l = new ArrayList<String>();
		
		StringTokenizer st = new StringTokenizer( this.postCreateProcessMap.get(Executable.SamIdx) );
		while( st.hasMoreTokens() )
			l.add(st.nextToken());
		l.add( ReposUtils.getMultiSeqFastaPath(this.refRepos, this.refId,
						this.refName));
		return l;
	}
	
	
	/**
	 * @return the cmd to run gmap build
	 */
	public List<String> getGmapBuildCmdList() {
		List<String> l = new ArrayList<String>();
		
		StringTokenizer st = new StringTokenizer( this.postCreateProcessMap.get(Executable.GmapBuild) );
		while( st.hasMoreTokens() )
			l.add(st.nextToken());
		l.add( "--dir=" + ReposUtils.getReferenceDirPath(this.refRepos, this.refId) );
		l.add(ReposUtils.getMultiSeqFastaPath(this.refRepos,
				this.refId, this.refName));
		return l;
	}

}
