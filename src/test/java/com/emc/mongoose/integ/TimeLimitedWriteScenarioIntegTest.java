package com.emc.mongoose.integ;

import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.TimeUtil;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.integ.integTestTools.IntegConstants;
import com.emc.mongoose.integ.integTestTools.IntegLogManager;
import com.emc.mongoose.integ.integTestTools.SavedOutputStream;
import com.emc.mongoose.run.scenario.ScriptRunner;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;

/**
 * Created by olga on 08.07.15.
 * Covers TC #6(name: "Limit the single write load job w/ both data item count and timeout",
 * steps: all, dominant limit: time) in Mongoose Core Functional Testing
 * HLUC: 1.1.6.2, 1.1.6.4
 */
public class TimeLimitedWriteScenarioIntegTest {
	//
	private static SavedOutputStream savedOutputStream;
	//
	private static String createRunId = IntegConstants.LOAD_CREATE;
	private static final String DATA_SIZE = "1B", LIMIT_TIME = "1.minutes";
	private static final int LIMIT_COUNT = 1000000000, LOAD_THREADS = 10;
	private static long actualTimeMS;

	@BeforeClass
	public static void before()
	throws Exception {
		// Set new saved console output stream
		savedOutputStream = new SavedOutputStream(System.out);
		System.setOut(new PrintStream(savedOutputStream));
		//Create run ID
		createRunId += ":infinite:" + IntegConstants.FMT_DT.format(
			Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).getTime()
		);
		System.setProperty(RunTimeConfig.KEY_RUN_ID, createRunId);
		// If tests run from the IDEA full logging file must be set
		final String fullLogConfFile = Paths
			.get(System.getProperty(IntegConstants.USER_DIR_PROPERTY_NAME), Constants.DIR_CONF, IntegConstants.LOG_FILE_NAME)
			.toString();
		System.setProperty(IntegConstants.LOG_CONF_PROPERTY_KEY, fullLogConfFile);
		LogUtil.init();
		final Logger rootLogger = org.apache.logging.log4j.LogManager.getRootLogger();
		//Reload default properties
		final RunTimeConfig runTimeConfig = new  RunTimeConfig();
		RunTimeConfig.setContext(runTimeConfig);
		//run mongoose default scenario in standalone mode
		final Thread writeScenarioMongoose = new Thread(new Runnable() {
			@Override
			public void run() {
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_RUN_ID, createRunId);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_DATA_SIZE_MAX, DATA_SIZE);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_DATA_SIZE_MIN, DATA_SIZE);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_LOAD_LIMIT_TIME, LIMIT_TIME);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_LOAD_LIMIT_COUNT, LIMIT_COUNT);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_LOAD_THREADS, LOAD_THREADS);
				rootLogger.info(Markers.MSG, RunTimeConfig.getContext().toString());
				new ScriptRunner().run();
			}
		}, "writeScenarioMongoose");
		actualTimeMS = System.currentTimeMillis();
		writeScenarioMongoose.start();
		writeScenarioMongoose.join();
		IntegLogManager.waitLogger();
		writeScenarioMongoose.interrupt();
		actualTimeMS = System.currentTimeMillis() - actualTimeMS;
		System.setOut(savedOutputStream.getPrintStream());
	}

	@Test
	public void shouldReportInformationAboutSummaryMetricsFromConsole()
	throws Exception {
		Assert.assertTrue(savedOutputStream.toString().contains(IntegConstants.SUMMARY_INDICATOR));
		Assert.assertTrue(savedOutputStream.toString().contains(IntegConstants.SCENARIO_END_INDICATOR));
	}

	@Test
	public void shouldReportScenarioEndToMessageLogFile()
	throws Exception {
		//Read message file and search "Scenario End"
		final File messageFile = IntegLogManager.getMessageFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(messageFile));
		// Search line in file which contains "Scenario end" string.
		// Get out from the loop when line with "Scenario end" if found else returned line = null
		String line;
		do {
			line = bufferedReader.readLine();
		} while ((!line.contains(IntegConstants.SCENARIO_END_INDICATOR)) && line != null);

		//Check the message file contain report about scenario end. If not line = null.
		Assert.assertTrue(line.contains(IntegConstants.SCENARIO_END_INDICATOR));
	}

	@Test
	public void shuldRunScenarioFor1Minutes()
	throws Exception {
		final int precisionMillis = 2000;
		//1.minutes = 60000.milliseconds
		final long loadLimitTimeMillis = TimeUtil.getTimeValue(LIMIT_TIME) * 60000;
		Assert.assertTrue((actualTimeMS >= loadLimitTimeMillis - precisionMillis) && (actualTimeMS <= loadLimitTimeMillis + precisionMillis));
		//
		final File perfAvgFile = IntegLogManager.getPerfAvgFile(createRunId);
		BufferedReader bufferedReader = new BufferedReader(new FileReader(perfAvgFile));
		//
		final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
		Date startTime = null, finishTime = null;
		// Get start time of loads
		bufferedReader.readLine();
		String line = bufferedReader.readLine();
		Matcher matcher = IntegConstants.TIME_PATTERN.matcher(line);
		if (matcher.find()) {
			startTime = format.parse(matcher.group());
		}
		// Get finish time of loads
		final File perfSumFile = IntegLogManager.getPerfSumFile(createRunId);
		bufferedReader = new BufferedReader(new FileReader(perfSumFile));
		bufferedReader.readLine();
		line = bufferedReader.readLine();
		matcher = IntegConstants.TIME_PATTERN.matcher(line);
		if (matcher.find()) {
			finishTime = format.parse(matcher.group());
		}
		//
		long differenceTime;
		for (int i = 0; i < 5; i++) {
			differenceTime = finishTime.getTime() - startTime.getTime();
			Assert.assertTrue(
				differenceTime > loadLimitTimeMillis - precisionMillis &&
					differenceTime > loadLimitTimeMillis + precisionMillis
			);
		}
	}

	@Test
	public void shouldCreateAllFilesWithLogs()
	throws Exception {
		Path expectedFile = IntegLogManager.getMessageFile(createRunId).toPath();
		//Check that messages.log file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = IntegLogManager.getPerfAvgFile(createRunId).toPath();
		//Check that perf.avg.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = IntegLogManager.getPerfSumFile(createRunId).toPath();
		//Check that perf.sum.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = IntegLogManager.getPerfTraceFile(createRunId).toPath();
		//Check that perf.trace.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = IntegLogManager.getDataItemsFile(createRunId).toPath();
		//Check that data.items.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = IntegLogManager.getErrorsFile(createRunId).toPath();
		//Check that errors.log file is not created
		Assert.assertFalse(Files.exists(expectedFile));
	}

	@Test
	public void shouldGeneralStatusOfTheRunIsRegularlyReports()
	throws Exception {
		// Get perf.avg.csv file
		final File perfAvgFile = IntegLogManager.getPerfAvgFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(perfAvgFile));

		final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
		Matcher matcher;
		//
		bufferedReader.readLine();
		String line = bufferedReader.readLine();
		final List<Date> listTimeOfReports = new ArrayList<>();
		while (line != null) {
			matcher = IntegConstants.TIME_PATTERN.matcher(line);
			if (matcher.find()) {
				listTimeOfReports.add(format.parse(matcher.group()));
			}
			line = bufferedReader.readLine();
		}
		// Check period of reports is correct
		long firstTime, nextTime;
		// Period must be equal 10 sec
		final int period = RunTimeConfig.getContext().getLoadMetricsPeriodSec();
		// period must be equal 10 seconds = 10000 milliseconds
		Assert.assertEquals(10, period);
		//
		for (int i = 0; i < listTimeOfReports.size() - 1; i++) {
			firstTime = listTimeOfReports.get(i).getTime();
			nextTime = listTimeOfReports.get(i + 1).getTime();
			// period must be equal 10 seconds = 10000 milliseconds
			Assert.assertEquals(10000, (nextTime - firstTime));
		}
	}
}
