package com.emc.mongoose.core.impl.load.executor;
//
import com.emc.mongoose.common.conf.AppConfig;
//
import com.emc.mongoose.common.conf.DataRangesConfig;
import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.common.io.IOWorker;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.io.conf.FileIoConfig;
import com.emc.mongoose.core.api.io.task.FileIoTask;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.executor.FileLoadExecutor;
//
import com.emc.mongoose.core.impl.io.task.BasicFileIoTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 23.11.15.
 */
public class BasicFileLoadExecutor<T extends FileItem>
extends MutableDataLoadExecutorBase<T>
implements FileLoadExecutor<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final ExecutorService ioTaskExecutor;
	//
	public BasicFileLoadExecutor(
		final AppConfig appConfig, final FileIoConfig<T, ? extends Directory<T>> ioConfig,
		final int threadCount, final Input<T> itemInput, final long maxCount,
		final float rateLimit, final SizeInBytes sizeConfig, final DataRangesConfig rangesConfig
	) throws ClassCastException {
		super(
			appConfig, ioConfig, null, threadCount, itemInput, maxCount, rateLimit,
			sizeConfig, rangesConfig
		);
		ioTaskExecutor = new ThreadPoolExecutor(
			threadCount, threadCount, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<Runnable>(maxItemQueueSize), new IOWorker.Factory(getName())
		) {
			@Override @SuppressWarnings("unchecked")
			protected final <V> RunnableFuture<V> newTaskFor(final Runnable task, final V value) {
				return (RunnableFuture<V>) task;
			}
			//
			@Override @SuppressWarnings("unchecked")
			protected final void afterExecute(final Runnable task, final Throwable throwable) {
				if(throwable == null) {
					try {
						ioTaskCompleted((FileIoTask<T>) task);
					} catch(final RemoteException ignored) {
					}
				} else {
					ioTaskFailed(1, throwable);
				}
			}
		};
	}
	//
	@Override
	protected FileIoTask<T> getIOTask(final T item, final String nextNodeAddr) {
		return new BasicFileIoTask<>(item, (FileIoConfig<T, Directory<T>>) ioConfigCopy);
	}
	//
	@Override
	public <A extends IOTask<T>> int submitTasks(
		final List<A> tasks, final int from, final int to
	) throws RejectedExecutionException {
		int n = 0;
		for(int i = from; i < to; i ++) {
			if(null != submitTask(tasks.get(i))) {
				n ++;
			} else {
				break;
			}
		}
		return n;
	}
	//
	@Override
	public <A extends IOTask<T>> Future<A> submitTask(final A ioTask)
	throws RejectedExecutionException {
		return (Future<A>) ioTaskExecutor.<FileIoTask<T>>submit((FileIoTask<T>) ioTask);
	}
	//
	@Override
	protected void shutdownActually() {
		if(!isCircular) {
			ioTaskExecutor.shutdown();
		}
		super.shutdownActually();
	}
	//
	@Override
	protected void interruptActually() {
		LOG.debug(Markers.MSG, "Dropped {} active I/O tasks", ioTaskExecutor.shutdownNow().size());
		super.interruptActually();
	}
}
