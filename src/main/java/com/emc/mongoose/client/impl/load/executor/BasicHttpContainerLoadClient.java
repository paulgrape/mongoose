package com.emc.mongoose.client.impl.load.executor;
//
import com.emc.mongoose.client.api.load.executor.HttpContainerLoadClient;
//
import com.emc.mongoose.common.conf.AppConfig;
//
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.item.base.ItemSrc;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
import com.emc.mongoose.core.api.io.task.HttpContainerIOTask;
//
import com.emc.mongoose.core.impl.io.task.BasicHttpContainerTask;
//
import com.emc.mongoose.server.api.load.executor.HttpContainerLoadSvc;
//
import java.rmi.RemoteException;
import java.util.Map;
/**
 Created by kurila on 21.10.15.
 */
public class BasicHttpContainerLoadClient<
	T extends HttpDataItem, C extends Container<T>, W extends HttpContainerLoadSvc<T, C>
> extends LoadClientBase<C, W> implements HttpContainerLoadClient<T, C, W> {
	//
	public BasicHttpContainerLoadClient(
		final AppConfig appConfig, final HttpRequestConfig reqConfig, final String addrs[],
		final int connCountPerNode, final int threadCount,
		final ItemSrc<C> itemSrc, final long maxCount,
		final Map<String, W> remoteLoadMap
	) throws RemoteException {
		super(
			appConfig, reqConfig, addrs, connCountPerNode, threadCount, itemSrc, maxCount,
			remoteLoadMap
		);
	}
	//
	@Override
	protected HttpContainerIOTask<T, C> getIOTask(final C item, final String nodeAddr) {
		return new BasicHttpContainerTask<>(item, nodeAddr, (HttpRequestConfig) ioConfigCopy);
	}
	//
}
