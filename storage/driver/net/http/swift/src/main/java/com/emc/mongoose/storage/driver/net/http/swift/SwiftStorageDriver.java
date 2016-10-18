package com.emc.mongoose.storage.driver.net.http.swift;

import com.emc.mongoose.model.api.io.task.IoTask;
import com.emc.mongoose.model.api.item.Item;
import com.emc.mongoose.storage.driver.net.http.base.HttpStorageDriverBase;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.SocketConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URISyntaxException;

/**
 Created by andrey on 07.10.16.
 */
public class SwiftStorageDriver<I extends Item, O extends IoTask<I>>
extends HttpStorageDriverBase<I, O> {

	private final static Logger LOG = LogManager.getLogger();

	// TODO implement

	public SwiftStorageDriver(
		final String runId, final LoadConfig loadConfig, final StorageConfig storageConfig,
		final String srcContainer, final boolean verifyFlag, final SocketConfig socketConfig
	) throws IllegalStateException {
		super(runId, loadConfig, storageConfig, srcContainer, verifyFlag, socketConfig);
	}

	@Override
	protected final void applyMetaDataHeaders(final HttpHeaders httpHeaders) {
	}

	@Override
	protected final void applyAuthHeaders(
		final HttpMethod httpMethod, final String dstUriPath, final HttpHeaders httpHeaders
	) {
	}

	@Override
	public final void applyCopyHeaders(final HttpHeaders httpHeaders, final I obj)
	throws URISyntaxException {
	}
}