/**
 * 
 */
package com.pacbio.secondary.common.sys;

/**
 * Simple container for results of parallel {@link ExecService}
 * SysCommandExecutor processes. Note that the values obtained from this object
 * may not be meaningful, depending on how {@link SysCommandExecutor} was
 * configured (ie, whether
 * {@link SysCommandExecutor#setCaptureStdOutput(boolean)} was set false, or
 * {@link SysCommandExecutor#setRunAsync(boolean)} was set to true).
 * 
 * @author jmiller
 */
public class SysCommandResult {

	private String output;
	private int exitCode;
	private String command;

	protected void setExitCode(int exitCode) {
		this.exitCode = exitCode;
	}

	/**
	 * Get the exit code of the process
	 * 
	 * @return int 	exit code
	 */
	public int getExitCode() {
		return this.exitCode;
	}

	protected void setCmdOutput(String commandOutput) {
		this.output = commandOutput;
	}

	/**
	 * Get the output of the process
	 * @return String	output
	 */
	public String getCmdOutput() {
		return output;
	}

	/**
	 * Get the original command
	 * @return command
	 */
	public String getCommand() {
		return this.command;
	}

	public void setCommand(String runCommand) {
		this.command = runCommand;
	}

}
