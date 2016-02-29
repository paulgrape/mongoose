package com.emc.mongoose.core.impl.load.builder;
//
import com.emc.mongoose.common.conf.AppConfig;
//
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.io.conf.FileIOConfig;
import com.emc.mongoose.core.api.load.builder.DirectoryLoadBuilder;
import com.emc.mongoose.core.api.load.executor.DirectoryLoadExecutor;
//
import com.emc.mongoose.core.impl.io.conf.BasicFileIOConfig;
import com.emc.mongoose.core.impl.load.executor.BasicDirectoryLoadExecutor;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
/**
 Created by kurila on 26.11.15.
 */
public class BasicDirectoryLoadBuilder<
	T extends FileItem,
	C extends Directory<T>,
	U extends DirectoryLoadExecutor<T, C>
>
extends ContainerLoadBuilderBase<T, C, U>
implements DirectoryLoadBuilder<T, C, U> {
	//
	public BasicDirectoryLoadBuilder(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
	}
	//
	@Override
	protected FileIOConfig<T, C> getDefaultIoConfig() {
		return new BasicFileIOConfig<>();
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		// create parent directories
		final String parentDirectories = ioConfig.getNamePrefix();
		if(parentDirectories != null && !parentDirectories.isEmpty()) {
			try {
				Files.createDirectories(Paths.get(parentDirectories));
			} catch(final IOException e) {
				throw new IllegalStateException(
					"Failed to create target directories @ \"" + parentDirectories + "\""
				);
			}
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually() {
		return (U) new BasicDirectoryLoadExecutor<>(
			appConfig, (FileIOConfig<T, C>) ioConfig, null, threadCount,
			itemSrc == null ? getDefaultItemSrc() : itemSrc, maxCount, rateLimit
		);
	}
}
