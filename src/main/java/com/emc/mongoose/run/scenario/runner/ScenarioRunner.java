package com.emc.mongoose.run.scenario.runner;
// mongoose-common.jar
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
//
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.run.scenario.Chain;
import com.emc.mongoose.run.scenario.Rampup;
import com.emc.mongoose.run.scenario.Single;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
/**
 Created by kurila on 12.05.14.
 A scenario runner utility class.
 */
public final class ScenarioRunner
implements Runnable {
	//
	private static final Logger LOG = LogManager.getLogger();
	//
	public void run() {
		final RunTimeConfig localRunTimeConfig = RunTimeConfig.getContext();
		if (localRunTimeConfig != null) {
			final String scenarioName = localRunTimeConfig.getScenarioName();
			//
			try {
				switch(scenarioName) {
					case Constants.RUN_SCENARIO_SINGLE:
						new Single(localRunTimeConfig).run();
						break;
					case Constants.RUN_SCENARIO_CHAIN:
						new Chain(localRunTimeConfig).run();
						break;
					case Constants.RUN_SCENARIO_RAMPUP:
						new Rampup(localRunTimeConfig).run();
						break;
					default:
						throw new IllegalArgumentException(
							String.format("Incorrect scenario: \"%s\"", scenarioName)
						);
				}
			} catch(final ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException e ) {
				LogUtil.exception(LOG, Level.ERROR, e, "Failed to execute");
			} catch(final InvocationTargetException e) {
				LogUtil.exception(LOG, Level.ERROR, e.getTargetException(), "Failed to execute");
			}
			LOG.info(Markers.MSG, "Scenario end");
		}
	}
}
