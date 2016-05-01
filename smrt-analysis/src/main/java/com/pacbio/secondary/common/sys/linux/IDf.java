package com.pacbio.secondary.common.sys.linux;

import java.util.List;


/**
 * Define the interface of the df command
 * @author jmiller
 *
 */
public interface IDf {

	/**
	 * First column of data
	 * @return
	 */
	public abstract List<String> getFileSystems();

	/**
	 * Get the size of the filesystem. The format of the return value may depend
	 * on the option supplied. For example,
	 * 
	 * <pre>
	 * df -h [...] will return a human-readable string, like 10G
	 * df -BM [...] will return the size in MB blocks, like 5486574556
	 * </pre>
	 * 
	 * df changes the column name, depending on the option ("Size" if -h,
	 * "1K-blocks' if nothing supplied), but that is abstracted from the caller.
	 * 
	 * @param filesystem
	 * @return
	 */
	public abstract String getSize(String filesystem);

	/**
	 * Get the amount of space used. Second column of df output.
	 * @param filesystem
	 * @return
	 */
	public abstract String getUsed(String filesystem);

	/**
	 * Get the available space.
	 * @param filesystem
	 * @return
	 */
	public abstract String getAvailable(String filesystem);

	/**
	 * Get % use. Third column of output.
	 * @param filesystem
	 * @return
	 */
	public abstract String getUse(String filesystem);

	/**
	 * Get the partition that the file system is mounted on.
	 * @param filesystem
	 * @return
	 */
	public abstract String getMountedOn(String filesystem);

	/**
	 * Get the filesystem corresponding to an input file
	 * @param file
	 * @return
	 */
	public abstract String getFileSystem(String file);

	/**
	 * Run the command.
	 * @throws Exception
	 */
	public abstract void exec() throws Exception;
	
	/**
	 * Set the options to pass to df
	 * @param options
	 */
	public abstract void setOptions( String... options );
	
	
	/**
	 * The files or directories to execute df against.
	 * @param files
	 */
	public abstract void setFiles( String... files );

}