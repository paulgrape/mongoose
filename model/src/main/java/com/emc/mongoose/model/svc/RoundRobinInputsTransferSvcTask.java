package com.emc.mongoose.model.svc;

import com.emc.mongoose.common.collection.OptLockArrayBuffer;
import com.emc.mongoose.common.collection.OptLockBuffer;
import com.emc.mongoose.common.concurrent.SvcTask;
import com.emc.mongoose.common.concurrent.SvcTaskBase;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.io.Output;
import static com.emc.mongoose.common.Constants.BATCH_SIZE;

import java.io.EOFException;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 Created by andrey on 15.12.16.
 */
public final class RoundRobinInputsTransferSvcTask<T>
extends SvcTaskBase {
	
	private final Output<T> output;
	private final List<? extends Input<T>> inputs;
	private final int inputsCount;
	private final AtomicLong rrc = new AtomicLong();
	private final OptLockBuffer<T> deferredItems;

	public RoundRobinInputsTransferSvcTask(
		final Output<T> output, final List<? extends Input<T>> inputs, final List<SvcTask> svcTasks
	) {
		super(svcTasks);
		this.output = output;
		this.inputs = inputs;
		this.inputsCount = inputs.size();
		this.deferredItems = new OptLockArrayBuffer<>(BATCH_SIZE);
	}

	@Override
	protected final void invoke() {
		try {
			// 1st try to output all deferred items
			if(deferredItems.tryLock()) {
				try {
					int n = deferredItems.size();
					if(n > 0) {
						n = output.put(deferredItems);
						deferredItems.removeRange(0, n);
						// do not work with new items if there were deferred items
						return;
					}
					final Input<T> nextInput = inputs.get(
						(int) (rrc.getAndIncrement() % inputsCount)
					);
					final List<T> items = nextInput.getAll();
					if(items != null) {
						n = items.size();
						if(n > 0) {
							final int m = output.put(items);
							if(m < n) {
								// not all items was transferred w/o blocking
								// defer the remaining items for a future try
								deferredItems.addAll(items.subList(m, n));
							}
						}
					}
				} finally {
					deferredItems.unlock();
				}
			}
		} catch(final NoSuchObjectException ignored) {
		} catch(final EOFException e) {
			try {
				close();
			} catch(final IOException ee) {
				ee.printStackTrace(System.err);
			}
		} catch(final RemoteException e) {
			final Throwable cause = e.getCause();
			if(cause instanceof EOFException) {
				try {
					close();
				} catch(final IOException ee) {
					ee.printStackTrace(System.err);
				}
			} else {
				e.printStackTrace(System.err);
			}
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
	}

	@Override
	protected final void doClose() {
		inputs.clear();
		try {
			deferredItems.tryLock(SvcTask.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			deferredItems.clear();
			deferredItems.unlock();
		} catch(final InterruptedException e) {
			e.printStackTrace(System.err);
		}
	}
}