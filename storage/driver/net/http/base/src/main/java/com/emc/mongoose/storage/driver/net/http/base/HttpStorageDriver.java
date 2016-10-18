package com.emc.mongoose.storage.driver.net.http.base;

import com.emc.mongoose.model.api.io.task.IoTask;
import com.emc.mongoose.model.api.item.Item;
import com.emc.mongoose.storage.driver.net.base.NetStorageDriver;
import io.netty.handler.codec.http.HttpRequest;

import java.net.URISyntaxException;

/**
 Created by kurila on 30.08.16.
 */
public interface HttpStorageDriver<I extends Item, O extends IoTask<I>>
extends NetStorageDriver<I, O> {
	
	String SIGN_METHOD = "HmacSHA1";
	int REQ_LINE_LEN = 1024;
	int HEADERS_LEN = 2048;
	int CHUNK_SIZE = 8192;
	
	HttpRequest getHttpRequest(final O ioTask, final String nodeAddr)
	throws URISyntaxException;
}