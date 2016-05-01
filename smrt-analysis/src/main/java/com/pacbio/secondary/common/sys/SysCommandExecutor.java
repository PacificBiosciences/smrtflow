/**
 * 
 */
package com.pacbio.secondary.common.sys;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The class provides functionality to run system commands. The behavior can be
 * customized in the following ways: the working dir can be set, the command can
 * be run asynchronously or synchronously, std err and std out can merged or
 * separated, std err and std out capture can be turned off (useful for
 * processes that generated lots of std out), and a logger may be supplied.
 * 
 * <pre>
 * Usage:
 * 
 * Waits until the command finishes:
 * 
 * SysCommandExecutor se = new SysCommandExecutor( "bash","-c","ls -l | grep myfile*" );
 * se.setWorkingDirectory( "/home/foo");
 * int exitCode = se.runCommand();
 * String results = se.getCommandOutput();
 * 
 * Returns immediately, so you probably won't get the result of the command (ls is not a good example):
 * 
 * SysCommandExecutor se = new SysCommandExecutor( "bash","-c","ls -l | grep myfile*" );
 * se.setRunAsync(true);
 * se.setWorkingDirectory( "/home/foo");
 * int exitCode = se.runCommand(); // returns 0 if command starts
 * String invalidResults = se.getCommandOutput();
 * </pre>
 * 
 * 
 * Also, because this class implements {@link Callable}, you may run N executors
 * in parallel using {@link ExecService}.
 * 
 * @author jmiller
 */
public class SysCommandExecutor implements Callable<SysCommandResult> {

	private static final String SEP = System.getProperty("line.separator");

	private IProcessLogger outputLogger = null;
	private IProcessLogger errorLogger = null;

	private StringBuffer cmdOutputBuff = null;
	private StringBuffer cmdErrorBuff = null;

	private String workingDirectory = null;
	private Map<String, String> envMap = new HashMap<String, String>();

	private AsyncStreamReader cmdOutputThrd = null;
	private AsyncStreamReader cmdErrorThrd = null;
	private List<String> cmdList;

	/** Store the process' stdErr in a StringBuffer **/
	private boolean captureStdErr = true;

	/** Store the process' stdOut in a StringBuffer **/
	private boolean captureStdOut = true;

	/** Merge the process' stdErr and stdOut into the same InputStream. **/
	private boolean mergeStdOutErr = true;

	/** False if caller waits on the process **/
	private boolean runAsync;

	/**
	 * Constructor. Requires the executable command and arguments as a list. See
	 * {@link ProcessBuilder}
	 * 
	 * @param cmdList
	 *            //
	 */
	public SysCommandExecutor(List<String> cmdList) {
		this.cmdList = cmdList;
	}

	/**
	 * Convenience constructor. {@link ProcessBuilder}
	 * 
	 * @param cmdList
	 */
	public SysCommandExecutor(String... cmdList) {
		this.cmdList = new ArrayList<String>();
		for (String s : cmdList)
			this.cmdList.add(s);

	}

	/**
	 * Default == false. This means the the command is run synchronously. If
	 * false, the main thread blocks until the process completes. Else, the main
	 * thread returns immediately.
	 * 
	 * @param async
	 */
	public void setRunAsync(boolean async) {
		this.runAsync = async;
	}

	/**
	 * By default, std err is merged with std out, so that error messages are
	 * more easily correlated with output messages. In this scenario,
	 * {@link #getCommandError()} and {@link #setErrorLogger(IProcessLogger)}
	 * have no effect since all error messages are obtained from output methods.
	 * 
	 * @param b
	 */
	public void setMergeStdOutErr(boolean mergeStdErr) {
		this.mergeStdOutErr = mergeStdErr;
	}

	/**
	 * Provide a logging instance, so that output from the command is captured,
	 * even if {@link #setCaptureStdOutput(boolean)} is set to false.
	 * 
	 * @param logDevice
	 */
	public void setOutputLogger(IProcessLogger logDevice) {
		outputLogger = logDevice;
	}

	/**
	 * Default == true.
	 * 
	 * If true, std out from the command is captured in a StringBuffer which you
	 * can get by calling {@link #getCommandOutput()}. Note that if you're
	 * executing a long-running cmd that produces lots of output, you may want
	 * to set this to false, and supply a logger instead:
	 * {@link #setOutputLogger(IProcessLogger)}
	 * 
	 * @param b
	 */
	public void setCaptureStdOutput(boolean b) {
		this.captureStdOut = b;
	}

	/**
	 * Default == true.
	 * 
	 * 
	 * If true, std out from the command is captured in a StringBuffer which you
	 * can get by calling {@link #getCommandOutput()}. Note that if you're
	 * executing a long-running cmd that produces lots of output, you may want
	 * to set this to false, and supply a logger instead:
	 * {@link #setOutputLogger(IProcessLogger)}
	 * 
	 * @param b
	 */
	public void setErrorLogger(IProcessLogger logDevice) {
		errorLogger = logDevice;
	}

	/**
	 * Set the ProcessBuilders directory. See
	 * {@link ProcessBuilder#directory(File)}
	 * 
	 * @param workingDirectory
	 */
	public void setWorkingDirectory(String workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Add an environment var to the process.
	 * 
	 * @param name
	 * @param value
	 */
	public void setEnvironmentVar(String name, String value) {
		if (envMap == null)
			envMap = new HashMap<String, String>();
		envMap.put(name, value);
	}

	/**
	 * Get the output of the cmd, if captureStdOut == true, which it is, by
	 * default. Else return null.
	 * 
	 * @return std out
	 */
	public String getCommandOutput() {
		if (cmdOutputBuff == null)
			return null;
		return cmdOutputBuff.toString();
	}

	/**
	 * Get std error from the command if captureStdErr == true and
	 * mergeStdOutErr == false. If mergeStdOutErr == true, std error messages
	 * can be found in {@link #getCommandOutput()}. Else returns null.
	 * 
	 * @return std err
	 */
	public String getCommandError() {
		if (cmdErrorBuff == null)
			return null;
		return cmdErrorBuff.toString();
	}

	/**
	 * Run the cmd. Configure the executor, via setters, prior to calling this.
	 * 
	 * @return
	 * @throws Exception
	 */
	public int runCommand() throws Exception {
		/* run command */
		ProcessBuilder pb = new ProcessBuilder(this.cmdList);

		if (this.workingDirectory != null)
			pb.directory(new File(workingDirectory));

		if (this.envMap != null)
			pb.environment().putAll(this.envMap);

		pb.redirectErrorStream(this.mergeStdOutErr);

		Process process = pb.start();

		/* start output and error read threads */
		startOutputReadThread(process.getInputStream());
		if (!mergeStdOutErr)
			startErrorReadThread(process.getErrorStream());

		if (runAsync)
			// if we don't care about waiting or joining threads, just return
			// out
			return 0;

		join();

		return process.waitFor();
	}

	@Override
	public SysCommandResult call() throws Exception {
		SysCommandResult scr = new SysCommandResult();
		int exitCode = runCommand();
		scr.setExitCode(exitCode);
		scr.setCmdOutput(this.getCommandOutput());
		scr.setCommand( this.getRunCommand() );
		return scr;
	}

	/**
	 * Need to join, or we may not be able to get the std output, depending on
	 * how quickly it's added to the buffer.
	 */
	private void join() {
		try {
			this.cmdOutputThrd.join();
			if (this.cmdErrorThrd != null)
				this.cmdErrorThrd.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * A threaded reader is always created to drain std out
	 * 
	 * @param processOut
	 */
	private void startOutputReadThread(InputStream processOut) {
		if (captureStdOut) {
			cmdOutputBuff = new StringBuffer();
		}
		cmdOutputThrd = new AsyncStreamReader(processOut, cmdOutputBuff,
				outputLogger, "OUTPUT");
		cmdOutputThrd.start();
	}

	/**
	 * A threaded reader is created to drain std err only if std err and std out
	 * are not merged
	 * 
	 * @param processErr
	 */
	private void startErrorReadThread(InputStream processErr) {
		if (captureStdErr) {
			cmdErrorBuff = new StringBuffer();

		}
		cmdErrorThrd = new AsyncStreamReader(processErr, cmdErrorBuff,
				errorLogger, "ERROR");
		cmdErrorThrd.start();
	}

	/**
	 * Internal class that can read an output stream from process.
	 * 
	 * @author jmiller
	 */
	protected class AsyncStreamReader extends Thread {

		private StringBuffer captureBuffer = null;
		private InputStream inStream = null;
		private String thrdId = null;
		private boolean stop = false;
		private IProcessLogger procLogger = null;

		/**
		 * Constructor
		 * 
		 * @param inputStream
		 * @param buffer
		 * @param logDevice
		 * @param threadId
		 */
		public AsyncStreamReader(InputStream inputStream, StringBuffer buffer,
				IProcessLogger logDevice, String threadId) {
			inStream = inputStream;
			captureBuffer = buffer;
			thrdId = threadId;
			procLogger = logDevice;
		}

		/**
		 * Get output captured from process
		 * 
		 * @return
		 */
		public String getBuffer() {
			if (captureBuffer == null) {
				return "buffer is null. Change this by setting captureStdOutput/Error to true.";
			}
			return captureBuffer.toString();
		}

		/**
		 * Starts the reading thread, logs any exception information
		 */
		public void run() {
			try {
				readCommandOutput();
			} catch (Exception ex) {
				print("Exception in thread " + this.thrdId, ex);
			}
		}

		/**
		 * Read
		 * 
		 * @throws IOException
		 */
		private void readCommandOutput() throws IOException {
			BufferedReader bufOut = null;
			try {
				bufOut = new BufferedReader(new InputStreamReader(inStream));
				String line = null;
				while (true) {

					line = bufOut.readLine();

					if (stop == true)
						break;

					if (line == null)
						break;
					print(line);
				}
			} finally {
				bufOut.close();
			}
		}

		/**
		 * Force the reading to stop early
		 */
		public void stopReading() {
			stop = true;
		}

		/**
		 * Prints a line to the captureBuffer, if not null, and to the logger,
		 * if not null
		 * 
		 * @param line
		 */
		private void print(String line) {
			if (captureBuffer != null)
				captureBuffer.append(line + SEP);
			if (procLogger != null)
				procLogger.log(line);

		}

		/**
		 * Prints a line and stacktrace to the captureBuffer, if not null, and
		 * to the logger, if not null
		 * 
		 * @param line
		 * @param t
		 */
		private void print(String line, Throwable t) {
			if (captureBuffer != null) {
				captureBuffer.append(line + SEP);
			}
			if (procLogger != null) {
				procLogger.log(line, t);
			}
		}
	}

	/**
	 * Main method is for testing only,
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		SysCommandExecutor s = new SysCommandExecutor("ls", "/opt");
		s.runCommand();
		System.out.println("std out: " + s.getCommandOutput());
		// should be null since std err merged w/ std out
		System.out.println("std err: " + s.getCommandError());

		// produces an err msg on my machine, so should see std err
		s = new SysCommandExecutor(
				"/opt/smrtanalysis/analysis/bin/sawriter",
				"hu.sa",
				"/home/NANOFLUIDICS/jmiller/refUploaderJava/input/homo_sapiens_hg19.fasta",
				"-blt", "8", "-welter");
		s.setWorkingDirectory("/home/NANOFLUIDICS/jmiller");
		s.setMergeStdOutErr(false);
		s.runCommand();
		System.out.println("std out: " + s.getCommandOutput());
		System.out.println("std err: " + s.getCommandError());

		// produces an err msg on my machine, so should see std err in std out
		s = new SysCommandExecutor(
				"/opt/smrtanalysis/analysis/bin/sawriter",
				"hu.sa",
				"/home/NANOFLUIDICS/jmiller/refUploaderJava/input/homo_sapiens_hg19.fasta",
				"-blt", "8", "-welter");
		s.setWorkingDirectory("/home/NANOFLUIDICS/jmiller");
		s.runCommand();
		System.out.println("std out: " + s.getCommandOutput());

		// test a long process without waiting
		s = new SysCommandExecutor(
				"/home/NANOFLUIDICS/jmiller/workspace/software/assembly/pbpy/checkPackages.py");
		s
				.setWorkingDirectory("/home/NANOFLUIDICS/jmiller/workspace/software/assembly/pbpy");
		s.setRunAsync(true);
		s.runCommand();
		System.out.println("std out: " + s.getCommandOutput());
		System.out.println("std err: " + s.getCommandError());
		System.out.print("Not waiting");

		// test a long process with waiting
		s = new SysCommandExecutor("../pbpy/checkPackages.py");
		s
				.setWorkingDirectory("/home/NANOFLUIDICS/jmiller/workspace/software/assembly/pbpy");
		s.runCommand();
		System.out.println("std out: " + s.getCommandOutput());
		System.out.println("std err: " + s.getCommandError());

	}

	
	/**
	 * Get the cmd to run, or that was run
	 * @return
	 */
	public String getRunCommand() {
		StringBuffer sb = new StringBuffer();
		for( String s : this.cmdList )
			sb.append(s).append(" ");
		return sb.toString().trim();
	}

}