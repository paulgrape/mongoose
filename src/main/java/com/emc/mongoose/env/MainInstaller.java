package com.emc.mongoose.env;

import com.emc.mongoose.config.BundledDefaultsProvider;
import static com.emc.mongoose.Constants.APP_NAME;
import static com.emc.mongoose.Constants.USER_HOME;
import static com.emc.mongoose.config.CliArgUtil.ARG_PATH_SEP;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MainInstaller
extends JarResourcesInstaller {

	private static final List<String> RES_INSTALL_FILES = Collections.unmodifiableList(
		Arrays.asList(
			"config/defaults.json",
			"example/content/textexample",
			"example/content/zerobytes",
			"example/scenario/groovy/types/additional/copy_load_using_env_vars.groovy",
			"example/scenario/groovy/types/additional/load_type.groovy",
			"example/scenario/groovy/types/additional/update_and_read_variants.groovy",
			"example/scenario/groovy/types/chain.groovy",
			"example/scenario/groovy/types/chain_with_delay_using_env_vars.groovy",
			"example/scenario/groovy/types/parallel_shell_commands.groovy",
			"example/scenario/groovy/types/weighted.groovy",
			"example/scenario/groovy/default.groovy",
			"example/scenario/groovy/rampup.groovy",
			"example/scenario/js/systest/CopyUsingInputPath.js",
			"example/scenario/js/types/additional/copy_load_using_env_vars.js",
			"example/scenario/js/types/additional/load_type.js",
			"example/scenario/js/types/additional/update_and_read_variants.js",
			"example/scenario/js/types/chain.js",
			"example/scenario/js/types/chain_with_delay_using_env_vars.js",
			"example/scenario/js/types/parallel_shell_commands.js",
			"example/scenario/js/types/weighted.js",
			"example/scenario/js/default.js",
			"example/scenario/js/rampup.js",
			"example/scenario/py/types/additional/copy_load_using_env_vars.py",
			"example/scenario/py/types/additional/load_type.py",
			"example/scenario/py/types/additional/update_and_read_variants.py",
			"example/scenario/py/types/chain.py",
			"example/scenario/py/types/chain_with_delay_using_env_vars.py",
			"example/scenario/py/types/parallel_shell_commands.py",
			"example/scenario/py/types/weighted.py",
			"example/scenario/py/default.py",
			"example/scenario/py/rampup.py",
			"ext/mongoose-storage-driver-coop-net.jar",
			"ext/mongoose-storage-driver-coop-net-http.jar",
			"ext/mongoose-storage-driver-coop-net-http-atmos.jar",
			"ext/mongoose-load-step-type-weighted.jar",
			"ext/mongoose-storage-driver-coop.jar",
			"ext/mongoose-load-step-type-linear.jar",
			"ext/mongoose-storage-driver-coop-net-http-s3.jar",
			"ext/mongoose-storage-driver-preempt.jar",
			"ext/mongoose-storage-driver-coop-nio-fs.jar",
			"ext/mongoose-load-step-type-chain.jar",
			"ext/mongoose-storage-driver-coop-nio.jar",
			"ext/mongoose-storage-driver-coop-net-http-swift.jar"
		)
	);

	private final Path appHomePath;

	public MainInstaller() {
		final Config bundledDefaults;
		try {
			final Map<String, Object> schema = SchemaProvider.resolveAndReduce(
				APP_NAME, Thread.currentThread().getContextClassLoader()
			);
			bundledDefaults = new BundledDefaultsProvider().config(ARG_PATH_SEP, schema);
		} catch(final Exception e) {
			throw new IllegalStateException(
				"Failed to load the bundled default config from the resources", e
			);
		}
		final String appVersion = bundledDefaults.stringVal("run-version");
		System.out.println(APP_NAME + " v " + appVersion);
		appHomePath = Paths.get(USER_HOME, "." + APP_NAME, appVersion);
	}

	public final Path appHomePath() {
		return appHomePath;
	}

	@Override
	protected final List<String> resourceFilesToInstall() {
		return RES_INSTALL_FILES;
	}
}
