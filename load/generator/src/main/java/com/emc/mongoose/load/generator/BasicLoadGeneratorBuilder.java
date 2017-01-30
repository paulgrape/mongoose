package com.emc.mongoose.load.generator;

import com.emc.mongoose.common.api.ByteRange;
import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.common.io.ConstantStringInput;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.io.pattern.RangePatternDefinedInput;
import com.emc.mongoose.model.io.task.BasicIoTaskBuilder;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.io.task.IoTask.IoResult;
import com.emc.mongoose.model.io.task.data.mutable.BasicMutableDataIoTaskBuilder;
import com.emc.mongoose.model.io.task.IoTaskBuilder;
import com.emc.mongoose.model.io.task.path.BasicPathIoTaskBuilder;
import com.emc.mongoose.model.item.BasicItemNameInput;
import com.emc.mongoose.model.item.BasicMutableDataItemFactory;
import com.emc.mongoose.model.item.CsvFileItemInput;
import com.emc.mongoose.model.item.DataItem;
import com.emc.mongoose.model.item.IoResultsItemInput;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemFactory;
import com.emc.mongoose.model.item.ItemNamingType;
import com.emc.mongoose.model.item.ItemType;
import com.emc.mongoose.model.item.NewDataItemInput;
import com.emc.mongoose.model.io.IoType;

import static com.emc.mongoose.model.storage.StorageDriver.BUFF_SIZE_MIN;
import static com.emc.mongoose.ui.config.Config.ItemConfig.InputConfig;
import static com.emc.mongoose.ui.config.Config.ItemConfig.NamingConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig.LimitConfig;
import com.emc.mongoose.model.item.NewItemInput;
import com.emc.mongoose.model.storage.StorageDriver;
import com.emc.mongoose.ui.config.Config.ItemConfig.DataConfig.RangesConfig;
import com.emc.mongoose.ui.log.LogUtil;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 Created by andrey on 12.11.16.
 */
public class BasicLoadGeneratorBuilder<
	I extends Item, O extends IoTask<I, R>, R extends IoResult,
	T extends BasicLoadGenerator<I, O, R>
>
implements LoadGeneratorBuilder<I, O, R, T> {

	private final static Logger LOG = LogManager.getLogger();

	private ItemConfig itemConfig;
	private LoadConfig loadConfig;
	private ItemType itemType;
	private ItemFactory<I> itemFactory;
	private List<StorageDriver<I, O, R>> storageDrivers;
	private Input<I> itemInput = null;
	private SizeInBytes itemSizeEstimate = null;
	
	@Override
	public BasicLoadGeneratorBuilder<I, O, R, T> setItemConfig(final ItemConfig itemConfig) {
		this.itemConfig = itemConfig;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, R, T> setLoadConfig(final LoadConfig loadConfig) {
		this.loadConfig = loadConfig;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, R, T> setItemType(final ItemType itemType) {
		this.itemType = itemType;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, R, T> setItemFactory(final ItemFactory<I> itemFactory) {
		this.itemFactory = itemFactory;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, R, T> setStorageDrivers(
		final List<StorageDriver<I, O, R>> storageDrivers
	) {
		this.storageDrivers = storageDrivers;
		return this;
	}
	
	@Override @SuppressWarnings("unchecked")
	public BasicLoadGeneratorBuilder<I, O, R, T> setItemInput(final Input<I> itemInput) {
		this.itemInput = itemInput;
		if(!(itemInput instanceof IoResultsItemInput)) {
			this.itemSizeEstimate = estimateDataItemSize((Input<DataItem>) itemInput);
		}
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public T build()
	throws UserShootHisFootException {

		final IoType ioType = IoType.valueOf(loadConfig.getType().toUpperCase());
		final LimitConfig limitConfig = loadConfig.getLimitConfig();

		final Input<String> dstPathInput;
		final IoTaskBuilder<I, O, R> ioTaskBuilder;
		final long countLimit = limitConfig.getCount();
		final SizeInBytes sizeLimit = limitConfig.getSize();
		final boolean shuffleFlag = loadConfig.getGeneratorConfig().getShuffle();

		final InputConfig inputConfig = itemConfig.getInputConfig();

		if(ItemType.DATA.equals(itemType)) {
			final RangesConfig rangesConfig = itemConfig.getDataConfig().getRangesConfig();
			ioTaskBuilder = (IoTaskBuilder<I, O, R>) new BasicMutableDataIoTaskBuilder()
				.setFixedRanges(ByteRange.parseList(rangesConfig.getFixed()))
				.setRandomRangesCount(rangesConfig.getRandom())
				.setSizeThreshold(rangesConfig.getThreshold().get());
		} else if(ItemType.PATH.equals(itemType)){
			ioTaskBuilder = (IoTaskBuilder<I, O, R>) new BasicPathIoTaskBuilder();
		} else {
			throw new AssertionError("Not implemented yet");
		}
		
		String itemInputPath = inputConfig.getPath();
		if(itemInputPath != null && !itemInputPath.startsWith("/")) {
			itemInputPath = "/" + itemInputPath;
		}
		ioTaskBuilder.setSrcPath(itemInputPath);
		ioTaskBuilder.setIoType(IoType.valueOf(loadConfig.getType().toUpperCase()));
		
		if(!IoType.NOOP.equals(ioType)) { // prevent the storage connections if noop
			String authToken = null;
			try {
				for(final StorageDriver<I, O, R> nextDriver : storageDrivers) {
					if(authToken == null) {
						authToken = nextDriver.getAuthToken();
					} else {
						// distribute the auth token among the storage drivers
						nextDriver.setAuthToken(authToken);
					}
				}
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to communicate with remote storage driver");
			}
		}

		final String itemInputFile = inputConfig.getFile();
		if(itemInput == null) {
			itemInput = getItemInput(ioType, itemInputFile, itemInputPath);
			if(ItemType.DATA.equals(itemType)) {
				itemSizeEstimate = estimateDataItemSize((Input<DataItem>) itemInput);
			} else {
				itemSizeEstimate = new SizeInBytes(BUFF_SIZE_MIN);
			}
		}

		if(itemSizeEstimate != null && ItemType.DATA.equals(itemType)) {
			try {
				for(final StorageDriver<I, O, R> storageDriver : storageDrivers) {
					try {
						storageDriver.adjustIoBuffers(itemSizeEstimate, ioType);
					} catch(final RemoteException e) {
						LogUtil.exception(
							LOG, Level.WARN, e,
							"Failed to adjust the storage driver buffer sizes"
						);
					}
				}
			} catch(final Exception e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to estimate the average data item size"
				);
			} finally {
				try {
					itemInput.reset();
				} catch(final IOException e) {
					LogUtil.exception(LOG, Level.WARN, e, "Failed to reset the item input");
				}
			}
		}

		if(IoType.CREATE.equals(ioType) && ItemType.DATA.equals(itemType)) {
			dstPathInput = getDstPathInput();
		} else {
			dstPathInput = null;
		}

		return (T) new BasicLoadGenerator<>(
			itemInput, itemSizeEstimate, dstPathInput, ioTaskBuilder, countLimit, sizeLimit,
			shuffleFlag
		);
	}
	
	private SizeInBytes estimateDataItemSize(final Input<DataItem> itemInput) {
		final int maxCount = 0x100;
		final List<DataItem> items = new ArrayList<>(maxCount);
		int n = 0;
		try {
			while(n < maxCount) {
				n += itemInput.get(items, maxCount - n);
			}
		} catch(final EOFException ignored) {
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to estimate the average data item size");
		}
		
		long sumSize = 0;
		long minSize = Long.MAX_VALUE;
		long maxSize = Long.MIN_VALUE;
		long nextSize;
		if(n > 0) {
			try {
				for(int i = 0; i < n; i++) {
					nextSize = items.get(i).size();
					sumSize += nextSize;
					if(nextSize < minSize) {
						minSize = nextSize;
					}
					if(nextSize > maxSize) {
						maxSize = nextSize;
					}
				}
			} catch(final IOException e) {
				throw new AssertionError(e);
			}
			return minSize == maxSize ?
				new SizeInBytes(sumSize / n) : new SizeInBytes(minSize, maxCount, 1);
		}
		return null;
	}

	private Input<String> getDstPathInput()
	throws UserShootHisFootException {
		final Input<String> dstPathInput;
		final String t = itemConfig.getOutputConfig().getPath();
		if(t == null || t.isEmpty()) {
			final String dstPath = "/" + LogUtil.getDateTimeStamp();
			dstPathInput = new ConstantStringInput(dstPath);
			try {
				storageDrivers.get(0).createPath(dstPath);
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to create the items output path \"{}\"",
					dstPath
				);
			}
		} else { // copy mode
			dstPathInput = new RangePatternDefinedInput(t.startsWith("/") ? t : "/" + t);
			String dstPath = null;
			try {
				dstPath = dstPathInput.get();
				dstPathInput.reset();
				if(dstPath != null) {
					final int sepPos = dstPath.indexOf('/', 1);
					if(sepPos > 1) {
						// create only 1st level path
						dstPath = dstPath.substring(0, sepPos);
					}
					storageDrivers.get(0).createPath(dstPath);
				}
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to create the items output path \"{}\"",
					dstPath
				);
			}
		}
		return dstPathInput;
	}

	@SuppressWarnings("unchecked")
	private Input<I> getItemInput(
		final IoType ioType, final String itemInputFile, final String itemInputPath
	) throws UserShootHisFootException {
		
		if(itemInputFile == null || itemInputFile.isEmpty()) {

			final NamingConfig namingConfig = itemConfig.getNamingConfig();
			final ItemNamingType namingType = ItemNamingType.valueOf(
				namingConfig.getType().toUpperCase()
			);
			final String namingPrefix = namingConfig.getPrefix();
			final int namingLength = namingConfig.getLength();
			final int namingRadix = namingConfig.getRadix();
			final long namingOffset = namingConfig.getOffset();

			if(itemInputPath == null || itemInputPath.isEmpty()) {
				if(IoType.CREATE.equals(ioType) || IoType.NOOP.equals(ioType)) {
					final BasicItemNameInput itemNameInput = new BasicItemNameInput(
						namingType, namingPrefix, namingLength, namingRadix, namingOffset
					);
					if(itemFactory instanceof BasicMutableDataItemFactory) {
						final SizeInBytes size = itemConfig.getDataConfig().getSize();
						itemInput = (Input<I>) new NewDataItemInput(
							itemFactory, itemNameInput, size
						);
					} else {
						itemInput = new NewItemInput<>(itemFactory, itemNameInput);
					}
				} else {
					throw new UserShootHisFootException(
						"No input (file either path) is specified for non-create generator"
					);
				}
			} else {
				itemInput = new StorageItemInput<>(
					storageDrivers.get(0), itemFactory, itemInputPath, namingPrefix, namingRadix
				);
			}
		} else {
			try {
				itemInput = new CsvFileItemInput<>(Paths.get(itemInputFile), itemFactory);
			} catch(final NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to use the item input file \"{}\"", itemInputFile
				);
			}
		}

		return itemInput;
	}
}
