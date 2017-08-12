package com.emc.mongoose.tests.system.deprecated;

import com.emc.mongoose.api.common.SizeInBytes;
import com.emc.mongoose.api.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.deprecated.EnvConfiguredScenarioTestBase;
import static com.emc.mongoose.api.common.Constants.KEY_TEST_STEP_ID;

import com.emc.mongoose.ui.log.LogUtil;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.ThreadContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static com.emc.mongoose.api.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 Created by andrey on 07.06.17.
 */
@Ignore public class LoopByCountTest
extends EnvConfiguredScenarioTestBase {

	private static final int EXPECTED_LOOP_COUNT = 10;
	private static final int EXPECTED_STEP_TIME = 5;
	private static long ACTUAL_TEST_TIME;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		EXCLUDE_PARAMS.clear();
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_TYPE, Arrays.asList("fs", "s3", "swift"));
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_CONCURRENCY, Arrays.asList(1, 10));
		EXCLUDE_PARAMS.put(KEY_ENV_STORAGE_DRIVER_COUNT, Arrays.asList(2));
		EXCLUDE_PARAMS.put(
			KEY_ENV_ITEM_DATA_SIZE,
			Arrays.asList(
				new SizeInBytes(0), new SizeInBytes("10KB"), new SizeInBytes("100MB"),
				new SizeInBytes("10GB")
			)
		);
		STEP_ID = LoopByCountTest.class.getSimpleName();
		SCENARIO_PATH = Paths.get(
			getBaseDir(), DIR_SCENARIO, "systest", "LoopByCount.json"
		);
		ThreadContext.put(KEY_TEST_STEP_ID, STEP_ID);
		CONFIG_ARGS.add("--test-step-limit-time=" + EXPECTED_STEP_TIME);
		EnvConfiguredScenarioTestBase.setUpClass();
		if(SKIP_FLAG) {
			return;
		}
		SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
		ACTUAL_TEST_TIME = System.currentTimeMillis();
		SCENARIO.run();
		ACTUAL_TEST_TIME = (System.currentTimeMillis() - ACTUAL_TEST_TIME) / 1000;
		TimeUnit.SECONDS.sleep(10);
		LogUtil.flushAll();
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		EnvConfiguredScenarioTestBase.tearDownClass();
	}

	@Test
	public final void testDuration()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		assertEquals(EXPECTED_LOOP_COUNT * EXPECTED_STEP_TIME, ACTUAL_TEST_TIME, 25);
	}

	@Test
	public final void testTotalMetricsLogFile()
	throws Exception {
		assumeFalse(SKIP_FLAG);
		final List<CSVRecord> totalRecs = getMetricsTotalLogRecords();
		assertEquals(EXPECTED_LOOP_COUNT, totalRecs.size());
		for(final CSVRecord totalRec : totalRecs) {
			testTotalMetricsLogRecord(
				totalRec, IoType.CREATE, CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, 0,
				EXPECTED_STEP_TIME
			);
		}
	}
}