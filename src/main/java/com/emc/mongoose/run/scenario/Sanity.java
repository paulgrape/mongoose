package com.emc.mongoose.run.scenario;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.ServiceUtil;
//
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.item.base.ItemDst;
//
import com.emc.mongoose.core.impl.item.data.BasicHttpObject;
import com.emc.mongoose.core.impl.item.data.ContentSourceBase;
import com.emc.mongoose.core.impl.item.base.ItemBinFileDst;
import com.emc.mongoose.core.impl.item.base.ItemCSVFileDst;
//
import com.emc.mongoose.core.impl.item.base.LimitedQueueItemBuffer;
import com.emc.mongoose.core.impl.item.base.ListItemDst;
//
import com.emc.mongoose.core.impl.item.base.ListItemSrc;
import com.emc.mongoose.server.api.load.builder.LoadBuilderSvc;
import com.emc.mongoose.storage.mock.impl.web.Cinderella;
//
import com.emc.mongoose.util.builder.MultiLoadBuilderSvc;
import com.emc.mongoose.util.client.api.StorageClient;
import com.emc.mongoose.util.client.api.StorageClientBuilder;
import com.emc.mongoose.util.client.impl.BasicStorageClientBuilder;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
/**
 Created by andrey on 22.06.15.
 */
public class Sanity
implements Runnable {
	//
	private final static short DEFAULT_NODE_COUNT = 2, DEFAULT_CONN_PER_NODE = 200;
	private final static long DEFAULT_DATA_SIZE = SizeInBytes.toFixedSize("10MB");
	private final static int DEFAULT_DATA_COUNT_MAX = 10000;
	public final static Logger LOG;
	static {
		LogUtil.init();
		LOG = LogManager.getLogger();
	}
	//
	private final StorageClient<HttpDataItem> client;
	//
	public Sanity(final StorageClient<HttpDataItem> client) {
		this.client = client;
	}
	//
	public void run() {
		try {
			final List<HttpDataItem> itemBuff = new ArrayList<>(DEFAULT_DATA_COUNT_MAX);
			// create new items
			LOG.info(Markers.MSG, "Start writing");
			final long nWritten = client.write(
				null, new ListItemDst<>(itemBuff), DEFAULT_DATA_COUNT_MAX, DEFAULT_CONN_PER_NODE,
				DEFAULT_DATA_SIZE
			);
			LOG.info(Markers.MSG, "Written successfully {} items", nWritten);
			/* update the created items
			LOG.info(Markers.MSG, "Start updating {} items", itemBuff.size());
			final ItemDst<HttpDataItem> dataDstU = new ItemBinFileDst<>();
			final long nUpdated = client.update(
				new ListItemSrc<>(itemBuff), dataDstU, nWritten, DEFAULT_CONN_PER_NODE, 10
			);
			LOG.info(Markers.MSG, "Updated successfully {} items", nUpdated);
			// read and verify the updated items
			final ItemDst<HttpDataItem> dataDstR = new ItemCSVFileDst<>(
				(Class<? extends HttpDataItem>) BasicHttpObject.class, ContentSourceBase.getDefault()
			);
			final long nRead = client.read(
				dataDstU.getItemSrc(), dataDstR, nUpdated, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead);
			// variable-sized appending of the verified data items
			final ItemDst<HttpDataItem> dataDstA = new LimitedQueueItemBuffer<>(
				new ArrayBlockingQueue<HttpDataItem>(DEFAULT_DATA_COUNT_MAX)
			);
			final long nAppended = client.append(
				dataDstR.getItemSrc(), dataDstA, nRead, DEFAULT_CONN_PER_NODE,
				DEFAULT_DATA_SIZE, 3 * DEFAULT_DATA_SIZE, 1
			);
			LOG.info(Markers.MSG, "Appended successfully {} items", nAppended);
			// update again the appended data items
			final Path fileTmpItems0 = Files.createTempFile("reUpdatedItems", ".csv"); // do not delete on exit
			final ItemDst<HttpDataItem> dataDstU2 = new ItemCSVFileDst<>(
				fileTmpItems0, (Class<? extends HttpDataItem>) BasicHttpObject.class,
				ContentSourceBase.getDefault()
			);
			final long nUpdated2 = client.update(
				dataDstA.getItemSrc(), dataDstU2, nAppended, DEFAULT_CONN_PER_NODE, 10
			);
			LOG.info(Markers.MSG, "Updated again successfully {} items", nUpdated2);
			// read and verify the updated items again
			final long nRead2 = client.read(
				dataDstU2.getItemSrc(), null, nUpdated2, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead2);*/
			// recreate the items
			final ItemDst<HttpDataItem> dataDstW2 = new ItemCSVFileDst<>(
				(Class<? extends HttpDataItem>) BasicHttpObject.class,
				ContentSourceBase.getDefault()
			);
			final long nReWritten = client.write(
				new ListItemSrc<>(itemBuff), dataDstW2, nWritten, DEFAULT_CONN_PER_NODE,
				DEFAULT_DATA_SIZE
			);
			LOG.info(Markers.MSG, "Rewritten successfully {} items", nReWritten);
			// read and verify the rewritten data items
			final long nRead3 = client.read(
				dataDstW2.getItemSrc(), null, nWritten, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead3);
			// delete all created data items
			final long nDeleted = client.delete(
				new ListItemSrc<>(itemBuff), null, nWritten, DEFAULT_CONN_PER_NODE
			);
			LOG.info(Markers.MSG, "Deleted successfully {} items", nDeleted);
		} catch(final Exception e) {
			e.printStackTrace(System.out);
			LogUtil.exception(LOG, Level.ERROR, e, "Sanity failure");
		}
	}
	//
	@SuppressWarnings("unchecked")
	public static void main(final String... args)
	throws IOException, InterruptedException {
		//
		final AppConfig appConfig = BasicConfig.THREAD_CONTEXT.get();
		//
		appConfig.setProperty(AppConfig.KEY_STORAGE_HTTP_MOCK_CAPACITY, DEFAULT_DATA_COUNT_MAX);
		appConfig.setProperty(AppConfig.KEY_STORAGE_HTTP_MOCK_CONTAINER_CAPACITY, DEFAULT_DATA_COUNT_MAX);
		appConfig.setProperty(AppConfig.KEY_STORAGE_HTTP_MOCK_HEAD_COUNT, DEFAULT_NODE_COUNT);
		//appConfig.setProperty(AppConfig.KEY_LOAD_METRICS_PERIOD_SEC, 0);
		Thread wsMockThread = new Thread(
			new Cinderella(BasicConfig.THREAD_CONTEXT.get()), "wsMock"
		);
		wsMockThread.setDaemon(true);
		wsMockThread.start();
		//
		appConfig.setProperty(AppConfig.KEY_LOAD_METRICS_PERIOD, 10);
		final StorageClientBuilder<HttpDataItem, StorageClient<HttpDataItem>>
			clientBuilder = new BasicStorageClientBuilder<>();
		final String storageNodes[] = new String[DEFAULT_NODE_COUNT];
		for(int i = 0; i < DEFAULT_NODE_COUNT; i++) {
			storageNodes[i] = "127.0.0.1:" + (9020 + i);
		}
		clientBuilder
			.setNodes(storageNodes)
			.setAuth("wuser1@sanity.local", "H1jTDL869wgZapHsylVcSYTx3aM7NxVABy8h017Z")
			.setLimitCount(DEFAULT_DATA_COUNT_MAX)
			.setLimitTime(0, TimeUnit.SECONDS)
			.setLimitRate(10000);
		// standalone
		try(final StorageClient<HttpDataItem> client = clientBuilder.build()) {
			final Thread sanityThread1 = new Thread(new Sanity(client), "sanityStandalone");
			sanityThread1.start();
			LOG.info(Markers.MSG, "Standalone sanity started");
			sanityThread1.join();
			LOG.info(Markers.MSG, "Standalone sanity finished");
		}
		// distributed mode
		appConfig.setProperty(AppConfig.KEY_NETWORK_SERVE_JMX, true);
		ServiceUtil.init();
		//
		final LoadBuilderSvc multiSvc = new MultiLoadBuilderSvc(appConfig);
		multiSvc.start();
		try(
			final StorageClient<HttpDataItem> client = clientBuilder
				.setClientMode(new String[] {ServiceUtil.getHostAddr()})
				.build()
		) {
			final Thread sanityThread2 = new Thread(new Sanity(client), "sanityDistributed");
			sanityThread2.start();
			LOG.info(Markers.MSG, "Distributed sanity started");
			sanityThread2.join();
			LOG.info(Markers.MSG, "Distributed sanity finished");
		}
		//
		multiSvc.close();
		ServiceUtil.shutdown();
		// finish
		wsMockThread.interrupt();
		LOG.info(Markers.MSG, "Storage mock stopped");
		LOG.info(Markers.MSG, "Sanity done");
	}
}
