/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.model.Reference.IndexFile;
import com.pacbio.secondary.common.model.Reference.IndexFileType;
import com.pacbio.secondary.common.model.ReferenceEntry;
import com.pacbio.secondary.common.model.ReferenceEntry.State;
import com.pacbio.secondary.common.model.ReferenceIndex;
import com.pacbio.secondary.common.model.ReferenceInfo;
import com.pacbio.secondary.common.sys.IProcessLogger;
import com.pacbio.secondary.common.sys.SysCommandExecutor;

/**
 * Sets the state of an existing reference to hidden.
 * 
 * @author jmiller
 */
public class CmdRefresh extends CmdBase {

	static final String SEP = System.getProperty("file.separator");
	
	private final static Logger LOGGER = Logger.getLogger(CmdRefresh.class
			.getName());


	private String bwt2sa = "bwt2sa";

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.pacbio.secondary.analysis.referenceUploader.CmdBase#run()
	 */
	@Override
	public void run() throws Exception {

		List<String> refDirs = getRefDirNames();
		if( refDirs.isEmpty() ) {
			LOGGER.info( "Nothing to refresh. " + refRepos + " is empty");
			return;
		}
		
		
		ReferenceIndex ri = ReferenceIndex.open(this.refRepos);
		for( String dirId : refDirs ) {
			ReferenceEntry re = ri.getEntry(dirId);
			if( re == null ) {
				try {
					processDropInRef(dirId);
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "failed to import " + dirId + " into the repository.", e);
				}
			}
		}
		

	}

	private void processDropInRef(String dirId) throws Exception {
		
		File refDir = new File(ReposUtils.getReferenceDirPath(this.refRepos, dirId));
		
		ReferenceInfo ri = ReferenceInfo.load( refDir );
		IndexFile sa = ri.getReference().getIndexFile(IndexFileType.Sa);
		
		if( currentlyContainsBwt(sa) ) {
			String newName = ri.getReference().getFile().getValue() + ".sa";
			
			String absBwt = ReposUtils.getAbsPath(this.refRepos, dirId, sa.getValue());
			String absSa = ReposUtils.getAbsPath( this.refRepos, dirId, newName );
			execBwt2Sa( absBwt, absSa );
			ri.getReference().getIndexFile(IndexFileType.Sa).setValue(newName);
			ri.save(refDir);
		}
		
		updateIndexXml(ri);
		
	}

	private void updateIndexXml(ReferenceInfo refInfo) throws Exception {
		ReferenceIndex ri = null;
		try {
			ri = ReferenceIndex.openForEdit(this.refRepos);
			ReferenceEntry re = new ReferenceEntry();
			
//			ri.addEntry(re);
			
			re.setId(refInfo.getId());
			re.setDirectory(refInfo.getId());
			re.setState(State.Active);
			re.setType(refInfo.getReference().getType());
			re.setVersion( refInfo.getVersion());
			re.setName(refInfo.getId());
			re.setMetadata(ReposUtils.getReferenceInfoXmlPath(refInfo.getId()) );
			ri.addEntry(re);
			ri.save();
		} finally {
			unlock(ri);
		}
		
		ReferenceChecksum rc = new ReferenceChecksum(refInfo.getId(), refRepos);
		rc.addChecksum();
		
	}

	private void execBwt2Sa(String absBwt, String absSa) throws Exception {
		SysCommandExecutor s = new SysCommandExecutor(bwt2sa, absBwt, absSa );
		s.setOutputLogger(new IProcessLogger() {
			
			@Override
			public void log(String line, Throwable t) {
				LOGGER.log(Level.SEVERE, line, t);
				
			}
			
			@Override
			public void log(String line) {
				LOGGER.log(Level.INFO, line);
			}
		});
		s.setRunAsync(false);
		s.runCommand();
	}

	private boolean currentlyContainsBwt(IndexFile sa) {
		if( sa == null )
			return false;
		return sa.getValue().endsWith(".bwt");
	}

	/**
	 * Reference dir names, not paths. These are equivalent to ids.
	 * 
	 * @return
	 */
	private List<String> getRefDirNames() {
		List<String> l = new ArrayList<String>();
		for (File f : new File(refRepos).listFiles(dirs())) {
			l.add(f.getName());
		}
		return l;
	}

	private FileFilter dirs() {
		return new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		};
	}

	/**
	 * Set the executable path
	 * @param bwt2Sa
	 */
	public void setBwt2SaExecutable(String bwt2Sa) {
		this.bwt2sa  = bwt2Sa;
	}

}
