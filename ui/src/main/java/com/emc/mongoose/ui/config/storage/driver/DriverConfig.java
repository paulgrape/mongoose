package com.emc.mongoose.ui.config.storage.driver;

import com.emc.mongoose.ui.config.storage.driver.queue.QueueConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 Created by andrey on 05.07.17.
 */
public final class DriverConfig
implements Serializable {

	public static final String KEY_ADDRS = "addrs";
	public static final String KEY_PORT = "port";
	public static final String KEY_QUEUE = "queue";
	public static final String KEY_REMOTE = "remote";
	public static final String KEY_THREADS = "threads";
	public static final String KEY_TYPE = "type";

	public final void setAddrs(final List<String> addrs) {
		this.addrs = addrs;
	}

	public final void setPort(final int port) {
		this.port = port;
	}

	public final void setQueueConfig(final QueueConfig queueConfig) {
		this.queueConfig = queueConfig;
	}

	public final void setRemote(final boolean remote) {
		this.remote = remote;
	}

	public final void setThreads(final int count) {
		this.threads = count;
	}

	public final void setType(final String type) {
		this.type = type;
	}

	@JsonProperty(KEY_ADDRS) private List<String> addrs;
	@JsonProperty(KEY_PORT) private int port;
	@JsonProperty(KEY_QUEUE) private QueueConfig queueConfig;
	@JsonProperty(KEY_REMOTE) private boolean remote;
	@JsonProperty(KEY_THREADS) private int threads;
	@JsonProperty(KEY_TYPE) private String type;

	public DriverConfig() {
	}

	public DriverConfig(final DriverConfig other) {
		this.addrs = new ArrayList<>(other.getAddrs());
		this.port = other.getPort();
		this.queueConfig = new QueueConfig(other.getQueueConfig());
		this.remote = other.getRemote();
		this.threads = other.getThreads();
		this.type = other.getType();
	}

	public final List<String> getAddrs() {
		return addrs;
	}

	public final int getPort() {
		return port;
	}

	public final QueueConfig getQueueConfig() {
		return queueConfig;
	}

	public final boolean getRemote() {
		return remote;
	}

	public final int getThreads() {
		return threads;
	}

	public final String getType() {
		return type;
	}
}
