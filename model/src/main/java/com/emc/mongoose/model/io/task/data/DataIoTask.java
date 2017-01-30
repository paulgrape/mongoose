package com.emc.mongoose.model.io.task.data;

import com.emc.mongoose.common.api.ByteRange;
import com.emc.mongoose.model.io.task.IoTask;
import static com.emc.mongoose.model.io.task.IoTask.IoResult;
import com.emc.mongoose.model.item.DataItem;
import static com.emc.mongoose.model.io.task.data.DataIoTask.DataIoResult;

import java.util.List;
/**
 Created by kurila on 11.07.16.
 */
public interface DataIoTask<I extends DataItem, R extends DataIoResult>
extends IoTask<I, R> {
	
	interface DataIoResult<I extends DataItem>
	extends IoResult<I> {
		
		@Override
		I getItem();
		
		long getDataLatency();
		
		long getCountBytesDone();
	}
	
	@Override
	I getItem();
	
	@Override
	R getResult(
		final String hostAddr,
		final boolean useStorageDriverResult,
		final boolean useStorageNodeResult,
		final boolean useItemInfoResult,
		final boolean useIoTypeCodeResult,
		final boolean useStatusCodeResult,
		final boolean useReqTimeStartResult,
		final boolean useDurationResult,
		final boolean useRespLatencyResult,
		final boolean useDataLatencyResult,
		final boolean useTransferSizeResult
	);

	List<ByteRange> getFixedRanges();

	long getCountBytesDone();

	void setCountBytesDone(long n);

	long getRespDataTimeStart();

	void startDataResponse();
}

