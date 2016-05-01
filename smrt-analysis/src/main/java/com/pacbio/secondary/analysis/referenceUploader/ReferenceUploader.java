/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pacbio.secondary.common.core.AbstractAnalysisConfig;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;

import com.pacbio.secondary.analysis.referenceUploader.ReferenceUploaderArgs.Action;
import com.pacbio.secondary.common.core.AnalysisContext;

/**
 * Entry point to the referenceUploader application. Instantiates the correct
 * cmd, in response to user-args.
 * 
 * @author jmiller
 */
public class ReferenceUploader {

	private final static Logger LOGGER = Logger
			.getLogger(ReferenceUploader.class.getName());
	
	/**
	 * Constructor
	 * @param args	from the cmd line
	 * @throws ParseException
	 */
	public ReferenceUploader(String[] args) throws ParseException {
		ReferenceUploaderArgs.CreateInstance(args);
	}

	
	/**
	 * The entry point
	 * @param args
	 */
	public static void main(String[] args) {
		
		try {
			
			ReferenceUploader ru = new ReferenceUploader(args);
			
			//exit immediately for help
			if(ReferenceUploaderArgs.instance().showHelp() ) {
				return;
			}
		
			if(ReferenceUploaderArgs.instance().verbose() ) {
				AnalysisContext.getInstance().initLogger("com.pacbio.secondary", "referenceUploader", Level.FINE);
				ConsoleHandler c = new ConsoleHandler();
				c.setLevel(Level.FINE);
				Logger.getLogger("com.pacbio.secondary").addHandler(c);
				
			}  else {
				AnalysisContext.getInstance().initLogger(ReferenceUploader.class.getPackage().getName(), "referenceUploader", Level.INFO);
			}
			LOGGER.info( Help.instance().toString() );
			ru.run();
			
			
		} catch (ParseException e) {
			LOGGER.log(Level.SEVERE, e.getMessage() );
			ReferenceUploaderArgs.getUsage();
			System.exit(1);//failure
			
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failure to complete cmd.", e);
			System.err.println( e.getMessage() );
			System.exit(1);//failure
		}
//		System.exit(1);//failure
	}


	private void run() throws Exception {
		LOGGER.log(Level.INFO, "The cmdline is: " + ReferenceUploaderArgs.instance().getCmdLine() );
		LOGGER.log(Level.INFO, "Starting " + ReferenceUploaderArgs.instance().getAction() );
		
		CmdBase c = getCommand();
		if( c == null ) {
			LOGGER.log(Level.INFO, "No command found.");
			return;
		}
		c.setReferenceRepos(ReferenceUploaderArgs.instance().getRefRepos());
		c.run();
		
		LOGGER.log(Level.INFO, "Finished");
	}


	private CmdBase getCommand() {
		
		if( ReferenceUploaderArgs.instance().getAction() == Action.Create ) {
			CmdCreate c = new CmdCreate();
			c.setFastaFiles( ReferenceUploaderArgs.instance().getFastaFiles() );
			c.setName( ReferenceUploaderArgs.instance().getName() );

            // MK. Always run samtools. These should just use a 'which' to resolve the exes if the 'default' exes
            // aren't provided
			String samToolExe = "samtools faidx";
            String saWriterExe = "sawriter -blt 8 -welter";
            c.addPostCreationExecutable( ProcessHelper.Executable.SAW, saWriterExe);
            c.addPostCreationExecutable( ProcessHelper.Executable.SamIdx, samToolExe);
			//c.addPostCreationExecutable( ProcessHelper.Executable.S, ReferenceUploaderArgs.instance().getSamIdxCommand() );
			//c.addPostCreationExecutable( ProcessHelper.Executable.GmapBuild, ReferenceUploaderArgs.instance().getGmapDbCommand() );
			//LOGGER.log(LOGGER.info,;

            c.setOrganism(ReferenceUploaderArgs.instance().getOrganism() );
			c.setUser( ReferenceUploaderArgs.instance().getUser() );
			c.setDescription( getDescription() );
			c.setPloidy( ReferenceUploaderArgs.instance().getPloidy() );
			c.setRefType( ReferenceUploaderArgs.instance().getRefType() );

            //c.setProgressFrequency(ReferenceUploaderArgs.instance().getProgressFrequency()) ;
            // MK. This doesn't make sense in the new model
			//c.skipUpdateIndexXml( ReferenceUploaderArgs.instance().getSkipUpdateIndexXml() );
			c.setFastaLineLength( ReferenceUploaderArgs.instance().getLineLength() );
			c.setJobId( ReferenceUploaderArgs.instance().getJobId() );
			
			//push system version into xml
			c.setVersion(Help.instance().getVersion() );
			return c;
			
		} else if ( ReferenceUploaderArgs.instance().getAction() == Action.Hide ) {
			CmdHide c = new CmdHide();
			c.setRefId( ReferenceUploaderArgs.instance().getRefId() );
			return c;
			
		} else if ( ReferenceUploaderArgs.instance().getAction() == Action.Activate ) {
			CmdActivate c = new CmdActivate();
			c.setRefId( ReferenceUploaderArgs.instance().getRefId() );
			return c;
			
		} else if ( ReferenceUploaderArgs.instance().getAction() == Action.Delete ) {
			CmdDelete c = new CmdDelete();
			c.setRefId( ReferenceUploaderArgs.instance().getRefId() );
			return c;
			
		} else if ( ReferenceUploaderArgs.instance().getAction() == Action.Refresh ) {
			CmdRefresh c = new CmdRefresh();
			return c;
			
		} else if ( ReferenceUploaderArgs.instance().getAction() == Action.Upgrade ) {
			CmdUpgrade c = new CmdUpgrade();
			c.setRefId(ReferenceUploaderArgs.instance().getRefId());
			c.setName(ReferenceUploaderArgs.instance().getName());
			c.setArgbag( ReferenceUploaderArgs.instance().getUpgradeArgbag() );
			return c;
		}
		
		return null;
	}

	/**
	 * Get the desc that winds up in reference.info.xml
	 * @return
	 */
	private String getDescription() {
		if( StringUtils.isNotBlank(ReferenceUploaderArgs.instance().getDesc()) ){
			return ReferenceUploaderArgs.instance().getDesc();
		}
		return ReferenceUploaderArgs.instance().getName();
	}

}
