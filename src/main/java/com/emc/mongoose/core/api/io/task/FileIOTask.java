package com.emc.mongoose.core.api.io.task;
//
import com.emc.mongoose.core.api.data.FileItem;
//
import java.util.concurrent.Callable;
/**
 Created by andrey on 22.11.15.
 */
public interface FileIOTask<T extends FileItem>
extends DataIOTask<T>, Callable<FileIOTask<T>> {
}
