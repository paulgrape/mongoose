package com.emc.mongoose.tests.system;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.tests.system.base.HttpStorageDistributedScenarioTestBase;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.Frequency;
import org.apache.logging.log4j.Level;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.ThreadContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.emc.mongoose.common.Constants.KEY_JOB_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 Created by andrey on 19.01.17.
 Covered use cases:
 * 2.1.1.1.2. Small Data Items (10KB)
 * 2.2.3.1. Random Item Ids
 * 2.3.2. CSV File
 * 4.3. Medium Concurrency Level (100)
 * 6.1. Load Job Naming
 * 6.2.5. By Time
 * 8.1. Periodic Reporting
 * 8.4. I/O Traces Reporting
 * 9.2.1. Create New Items
 * 10.2. Default Scenario
 * 11.2. I/O Buffer Size Adjustment for Optimal Performance
 * 12.1.2. Two Local Separate Storage Driver Services (at different ports)
 */
public class CreateByTimeTest
extends HttpStorageDistributedScenarioTestBase {

	private static final SizeInBytes ITEM_DATA_SIZE = new SizeInBytes("10KB");
	private static final String ITEM_OUTPUT_FILE = CreateByTimeTest.class.getSimpleName() + ".csv";
	private static final int LOAD_LIMIT_TIME = 25;
	private static final int LOAD_CONCURRENCY = 100;

	private static boolean FINISHED_IN_TIME = true;
	private static String STD_OUTPUT = null;

	@BeforeClass public static void setUpClass()
	throws Exception {
		ThreadContext.put(KEY_JOB_NAME, CreateByTimeTest.class.getSimpleName());
		CONFIG_ARGS.add("--item-data-size=" + ITEM_DATA_SIZE.toString());
		CONFIG_ARGS.add("--item-output-file=" + ITEM_OUTPUT_FILE);
		CONFIG_ARGS.add("--load-limit-time=" + LOAD_LIMIT_TIME);
		CONFIG_ARGS.add("--load-concurrency=" + LOAD_CONCURRENCY);
		HttpStorageDistributedScenarioTestBase.setUpClass();
		final Thread runner = new Thread(
			() -> {
				try {
					STD_OUT_STREAM.startRecording();
					SCENARIO.run();
					STD_OUTPUT = STD_OUT_STREAM.stopRecording();
				} catch(final Throwable t) {
					LogUtil.exception(LOG, Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		runner.start();
		TimeUnit.SECONDS.timedJoin(runner, LOAD_LIMIT_TIME + 2);
		if(runner.isAlive()) {
			runner.interrupt();
			FINISHED_IN_TIME = false;
		}
		LoadJobLogFileManager.flush(JOB_NAME);
		TimeUnit.SECONDS.sleep(5);
	}

	@AfterClass public static void tearDownClass()
	throws Exception {
		HttpStorageDistributedScenarioTestBase.tearDownClass();
	}

	@Test public void testFinishedInTime()
	throws Exception {
		assertTrue(FINISHED_IN_TIME);
	}

	@Test public void testMetricsLogFile()
	throws Exception {
		testMetricsLogFile(
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, 0,
			LOAD_LIMIT_TIME, CONFIG.getLoadConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test public void testTotalMetricsLogFile()
	throws Exception {
		testTotalMetricsLogFile(
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, 0,
			LOAD_LIMIT_TIME
		);
	}

	@Test public void testMetricsStdout()
	throws Exception {
		testMetricsStdout(
			STD_OUTPUT.replaceAll("[\r\n]+", " "),
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE,
			CONFIG.getLoadConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test public void testIoTraceLogFile()
	throws Exception {
		final String nodeAddr = STORAGE_MOCKS.keySet().iterator().next();
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.CREATE.ordinal(), ITEM_DATA_SIZE);
			testHttpStorageMockContains(
				nodeAddr, ioTraceRecord.get("ItemPath"),
				Long.parseLong(ioTraceRecord.get("TransferSize"))
			);
		}
	}

	@Test public void testIoBufferSizeAdjustment()
	throws Exception {
		String msg = "Adjust output buffer size: " + ITEM_DATA_SIZE;
		int k;
		for(int i = 0; i < STORAGE_DRIVERS_COUNT; i ++) {
			k = STD_OUTPUT.indexOf(msg);
			if(k > -1) {
				msg = STD_OUTPUT.substring(k + msg.length());
			} else {
				fail("Expected the message to occur " + STORAGE_DRIVERS_COUNT + " times, but got " + i);
			}
		}
	}

	@Test public void testItemsOutputFile()
	throws Exception {
		final List<CSVRecord> items = new ArrayList<>();
		try(final BufferedReader br = new BufferedReader(new FileReader(ITEM_OUTPUT_FILE))) {
			final CSVParser csvParser = CSVFormat.RFC4180.withHeader().parse(br);
			for(final CSVRecord csvRecord : csvParser) {
				items.add(csvRecord);
			}
		}
		final int itemIdRadix = CONFIG.getItemConfig().getNamingConfig().getRadix();
		final Frequency freq = new Frequency();
		String itemPath, itemId;
		long itemOffset;
		long itemSize;
		String modLayerAndMask;
		for(final CSVRecord itemRec : items) {
			itemPath = itemRec.get(0);
			itemId = itemPath.substring(itemPath.lastIndexOf('/') + 1);
			itemOffset = Long.parseLong(itemRec.get(1), 0x10);
			assertEquals(Long.parseLong(itemId, itemIdRadix), itemOffset);
			freq.addValue(itemOffset);
			itemSize = Long.parseLong(itemRec.get(2));
			assertEquals(ITEM_DATA_SIZE.get(), itemSize);
			modLayerAndMask = itemRec.get(3);
			assertEquals("0/0", modLayerAndMask);
		}
		assertEquals(items.size(), freq.getUniqueCount());
	}
}