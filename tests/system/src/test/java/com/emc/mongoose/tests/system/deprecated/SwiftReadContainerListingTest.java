package com.emc.mongoose.tests.system.deprecated;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.env.PathUtil;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.deprecated.HttpStorageDistributedScenarioTestBase;
import com.emc.mongoose.tests.system.util.PortListener;
import com.emc.mongoose.ui.cli.CliArgParser;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.emc.mongoose.common.Constants.KEY_STEP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 Created by andrey on 01.06.17.
 Covered use cases:
 * 2.1.1.1.2. Small Data Items (10KB)
 * 2.2.2. Path Listing
 * 2.3.3.1. Constant Destination Path
 * 4.4. Big Concurrency Level (1K)
 * 8.3.2. Read - Enabled Validation
 * 9.2. Default Scenario
 * 9.5.2. Load Job
 * 10.1.4. Two Local Separate Storage Driver Services (at different ports)
 * 10.2.2. Destination Path Precondition Hook
 * 10.4.4. I/O Buffer Size Adjustment for Optimal Performance
 */

public class SwiftReadContainerListingTest
extends HttpStorageDistributedScenarioTestBase {

	private static final SizeInBytes ITEM_DATA_SIZE = new SizeInBytes("10KB");
	private static final String ITEM_OUTPUT_PATH = SwiftReadContainerListingTest
		.class.getSimpleName();
	private static final int LOAD_CONCURRENCY = 10;
	private static final int LOAD_LIMIT_TIME = 50;

	private static String STD_OUTPUT = null;
	private static int ACTUAL_CONCURRENCY = 0;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		STEP_NAME = SwiftReadContainerListingTest.class.getSimpleName();
		CONFIG_ARGS.add("--item-data-size=" + ITEM_DATA_SIZE.toString());
		CONFIG_ARGS.add("--item-output-path=" + ITEM_OUTPUT_PATH);
		CONFIG_ARGS.add("--storage-driver-concurrency=100");
		CONFIG_ARGS.add("--storage-driver-type=swift");
		CONFIG_ARGS.add("--storage-net-http-namespace=ns1");
		CONFIG_ARGS.add("--test-step-limit-time=" + LOAD_LIMIT_TIME);
		CONFIG_ARGS.add("--test-step-name=" + STEP_NAME);
		ThreadContext.put(KEY_STEP_NAME, STEP_NAME);
		HttpStorageDistributedScenarioTestBase.setUpClass();
		SCENARIO.run();

		// reinit
		SCENARIO.close();
		STEP_NAME = SwiftReadContainerListingTest.class.getSimpleName() + "_";
		FileUtils.deleteDirectory(Paths.get(PathUtil.getBaseDir(), "log", STEP_NAME).toFile());
		ThreadContext.put(KEY_STEP_NAME, STEP_NAME);
		LogUtil.init();
		CONFIG_ARGS.add("--item-data-verify");
		CONFIG_ARGS.add("--item-input-path=" + ITEM_OUTPUT_PATH);
		CONFIG_ARGS.add("--load-type=read");
		CONFIG_ARGS.add("--storage-driver-concurrency=" + LOAD_CONCURRENCY);
		CONFIG_ARGS.add("--test-step-name=" + STEP_NAME);
		CONFIG.apply(
			CliArgParser.parseArgs(
				CONFIG.getAliasingConfig(), CONFIG_ARGS.toArray(new String[CONFIG_ARGS.size()])
			)
		);
		CONFIG.getItemConfig().getOutputConfig().setPath(null);
		CONFIG.getTestConfig().getStepConfig().getLimitConfig().setTime(0);
		CONFIG.getTestConfig().getStepConfig().setName(STEP_NAME);
		SCENARIO = new JsonScenario(CONFIG, DEFAULT_SCENARIO_PATH.toFile());

		final Thread runner = new Thread(
			() -> {
				try {
					STD_OUT_STREAM.startRecording();
					SCENARIO.run();
					STD_OUTPUT = STD_OUT_STREAM.stopRecordingAndGet();
				} catch(final Throwable t) {
					LogUtil.exception(Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		runner.start();
		TimeUnit.SECONDS.sleep(10); // warmup
		final int startPort = CONFIG.getStorageConfig().getNetConfig().getNodeConfig().getPort();
		for(int i = 0; i < STORAGE_NODE_COUNT; i ++) {
			ACTUAL_CONCURRENCY += PortListener
				.getCountConnectionsOnPort("127.0.0.1:" + (startPort + i));
		}
		TimeUnit.MINUTES.timedJoin(runner, 5);
		runner.interrupt();
		LoadJobLogFileManager.flush(STEP_NAME);
		TimeUnit.SECONDS.sleep(10);
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		HttpStorageDistributedScenarioTestBase.tearDownClass();
	}


	public void testActiveConnectionsCount()
	throws Exception {
		assertEquals(STORAGE_DRIVERS_COUNT * LOAD_CONCURRENCY, ACTUAL_CONCURRENCY);
	}

	public void testMetricsLogFile()
	throws Exception {
		testMetricsLogRecords(
			getMetricsLogRecords(),
			IoType.READ, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, 0, 0,
			CONFIG.getTestConfig().getStepConfig().getMetricsConfig().getPeriod()
		);
	}


	public void testTotalMetricsLogFile()
	throws Exception {
		testTotalMetricsLogRecord(
			getMetricsTotalLogRecords().get(0),
			IoType.READ, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, 0, 0
		);
	}

	public void testMetricsStdout()
	throws Exception {
		testSingleMetricsStdout(
			STD_OUTPUT.replaceAll("[\r\n]+", " "),
			IoType.READ, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE,
			CONFIG.getTestConfig().getStepConfig().getMetricsConfig().getPeriod()
		);
	}

	public void testIoTraceLogFile()
	throws Exception {
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.READ.ordinal(), ITEM_DATA_SIZE);
		}
	}

	public void testIoBufferSizeAdjustment()
	throws Exception {
		String msg = "Adjust input buffer size: " + ITEM_DATA_SIZE.toString();
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
}