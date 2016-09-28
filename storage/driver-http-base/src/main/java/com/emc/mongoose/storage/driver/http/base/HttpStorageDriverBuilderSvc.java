package com.emc.mongoose.storage.driver.http.base;

import com.emc.mongoose.model.api.io.task.IoTask;
import com.emc.mongoose.model.api.item.Item;
import com.emc.mongoose.storage.driver.base.StorageDriverBuilderSvc;

/**
 Created on 28.09.16.
 */
public interface HttpStorageDriverBuilderSvc<
	I extends Item, O extends IoTask<I>, T extends HttpStorageDriver<I, O>
	>
extends StorageDriverBuilderSvc<I, O, T> {
	
}
