/**
 * 
 */
package com.pacbio.secondary.common.sys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This class is designed to run system commands in parallel. Each
 * {@link SysCommandExecutor} should be configured by the caller to run
 * synchronously or asynchronously. This determines whether the parallel threads
 * are joined. If run asynchronously, the results should be considered
 * meaningless. If run synchronously, you won't get the list of futures back
 * until they are all complete. However, you will be able to get a
 * {@link SysCommandResult} from each future.
 * 
 * @author jmiller
 */
public class ExecService {

	private ExecutorService executorService;

	/**
	 * Construct a service with a fixed number of threads
	 * 
	 * @param threadPoolSize
	 *            should be == nproc
	 */
	public ExecService(int threadPoolSize) {
		executorService = Executors.newFixedThreadPool(threadPoolSize);
	}

	/**
	 * If executing only 1 system command, is is recommended that you use
	 * {@link SysCommandExecutor#runCommand()} directly. This method will run
	 * your system commands in parallel, if possible, given the number of
	 * threads in the pool. This method returns when each
	 * {@link SysCommandExecutor} has finished. So, if each
	 * {@link SysCommandExecutor} has been set to run asynchronously, the method
	 * will return immediately, but the result probably won't be meaningful.
	 * 
	 * @param executors
	 * @return a list of Futures, from which you can get the
	 *         {@link SysCommandResult} of each thread
	 * @throws InterruptedException
	 */
	public List<Future<SysCommandResult>> execAll(
			SysCommandExecutor... executors) throws InterruptedException {
		Collection<Callable<SysCommandResult>> coll = new ArrayList<Callable<SysCommandResult>>();
		for (SysCommandExecutor se : executors) {
			coll.add((Callable<SysCommandResult>) se);
		}
		try {
			return executorService.invokeAll(coll);
		} finally {
			executorService.shutdown();
		}
	}

}