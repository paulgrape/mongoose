package com.emc.mongoose.storage.driver.preempt.mock;

import com.emc.mongoose.exception.OmgShootMyFootException;
import com.emc.mongoose.data.DataInput;
import com.emc.mongoose.item.io.IoType;
import com.emc.mongoose.item.io.task.IoTask;
import com.emc.mongoose.item.io.task.data.DataIoTask;
import com.emc.mongoose.item.DataItem;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.ItemFactory;
import com.emc.mongoose.storage.Credential;
import com.emc.mongoose.storage.driver.preempt.PreemptStorageDriverBase;

import com.github.akurilov.commons.collection.Range;
import com.github.akurilov.commons.math.Random;

import com.github.akurilov.confuse.Config;

import java.io.IOException;
import java.util.List;

public class PreemptStorageDriverMock<I extends Item, O extends IoTask<I>>
extends PreemptStorageDriverBase<I, O> {

	private final Random rnd = new Random();

	public PreemptStorageDriverMock(
		final String stepId, final DataInput itemDataInput, final Config loadConfig,
		final Config storageConfig, final boolean verifyFlag
	) throws OmgShootMyFootException {
		super(stepId, itemDataInput, loadConfig, storageConfig, verifyFlag);
	}

	@Override
	protected void execute(final O ioTask) {
		ioTask.startRequest();
		ioTask.finishRequest();
		ioTask.startResponse();
		if(ioTask instanceof DataIoTask) {
			final DataIoTask dataIoTask = (DataIoTask) ioTask;
			final DataItem dataItem = dataIoTask.item();
			switch(dataIoTask.ioType()) {
				case CREATE:
					try {
						dataIoTask.countBytesDone(dataItem.size());
					} catch(final IOException ignored) {
					}
					break;
				case READ:
					dataIoTask.startDataResponse();
					break;
				case UPDATE:
					final List<Range> fixedRanges = dataIoTask.fixedRanges();
					if(fixedRanges == null || fixedRanges.isEmpty()) {
						if(dataIoTask.hasMarkedRanges()) {
							dataIoTask.countBytesDone(dataIoTask.markedRangesSize());
						} else {
							try {
								dataIoTask.countBytesDone(dataItem.size());
							} catch(final IOException ignored) {
							}
						}
					} else {
						dataIoTask.countBytesDone(dataIoTask.markedRangesSize());
					}
					break;
				default:
					break;
			}
		}
		ioTask.finishResponse();
		ioTask.status(IoTask.Status.SUCC);
	}

	@Override
	protected String requestNewPath(final String path) {
		return path;
	}

	@Override
	protected String requestNewAuthToken(final Credential credential) {
		return Long.toHexString(rnd.nextLong());
	}

	@Override
	public List<I> list(
		final ItemFactory<I> itemFactory, final String path, final String prefix, final int idRadix,
		final I lastPrevItem, final int count
	) throws IOException {
		return null;
	}

	@Override
	public void adjustIoBuffers(final long avgTransferSize, final IoType ioType) {

	}
}