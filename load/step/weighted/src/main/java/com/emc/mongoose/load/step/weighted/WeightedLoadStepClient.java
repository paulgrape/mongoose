package com.emc.mongoose.load.step.weighted;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.item.io.IoType;
import com.emc.mongoose.load.step.client.LoadStepClientBase;
import com.emc.mongoose.logging.LogUtil;

import com.github.akurilov.commons.system.SizeInBytes;
import static com.github.akurilov.commons.collection.TreeUtil.reduceForest;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;
import static com.github.akurilov.confuse.Config.deepToMap;

import org.apache.logging.log4j.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class WeightedLoadStepClient
extends LoadStepClientBase {

	public WeightedLoadStepClient(
		final Config baseConfig, final List<Extension> extensions, final List<Map<String, Object>> overrides
	) {
		super(baseConfig, extensions, overrides);
	}

	@Override
	public String getTypeName() {
		return WeightedLoadStepExtension.TYPE;
	}

	@Override
	protected WeightedLoadStepClient copyInstance(final List<Map<String, Object>> stepConfigs) {
		return new WeightedLoadStepClient(baseConfig, extensions, stepConfigs);
	}

	@Override
	protected void init()
	throws IllegalStateException {

		final String autoStepId = "weighted_" + LogUtil.getDateTimeStamp();
		final Config config = new BasicConfig(baseConfig);
		final Config stepConfig = config.configVal("load-step");
		if(stepConfig.boolVal("idAutoGenerated")) {
			stepConfig.val("id", autoStepId);
		}
		actualConfig(config);

		final int subStepCount = stepConfigs.size();

		// 2nd pass: initialize the sub steps
		for(int originIndex = 0; originIndex < subStepCount; originIndex ++) {

			final Map<String, Object> mergedConfigTree = reduceForest(
				Arrays.asList(deepToMap(config), stepConfigs.get(subStepCount))
			);
			final Config subConfig;
			try {
				subConfig = new BasicConfig(config.pathSep(), config.schema(), mergedConfigTree);
			} catch(final InvalidValueTypeException | InvalidValuePathException e) {
				LogUtil.exception(Level.FATAL, e, "Scenario syntax error");
				throw new CancellationException();
			}

			final Config loadConfig = subConfig.configVal("load");
			final IoType ioType = IoType.valueOf(loadConfig.stringVal("type").toUpperCase());
			final int concurrency = loadConfig.intVal("step-limit-concurrency");
			final Config outputConfig = subConfig.configVal("output");
			final Config metricsConfig = outputConfig.configVal("metrics");
			final SizeInBytes itemDataSize = new SizeInBytes(subConfig.stringVal("item-data-size"));
			final int nodeCount = 1 + stepConfig.listVal("node-addrs").size();
			final boolean colorFlag = outputConfig.boolVal("color");

			initMetrics(originIndex, ioType, concurrency, nodeCount, metricsConfig, itemDataSize, colorFlag);
		}
	}
}
