package com.emc.mongoose.storage.driver.nio.fs;

import com.emc.mongoose.common.math.Random;
import com.emc.mongoose.model.item.BasicItemFactory;
import com.emc.mongoose.model.item.MutableDataItem;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 Created by andrey on 02.12.16.
 */
public class FileStorageDriverTest {

	private static Path TMP_DIR_PATH = null;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		try {
			TMP_DIR_PATH = Files.createTempDirectory(null);
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		if(TMP_DIR_PATH != null) {
			try {
				FileUtils.deleteDirectory(TMP_DIR_PATH.toFile());
			} catch(final IOException e) {
				e.printStackTrace(System.err);
			}
			TMP_DIR_PATH = null;
		}
	}

	@Test
	public final void testList()
	throws Exception {

		final String prefix = "yohoho";
		final int count = 1000;

		for(int i = 0; i < count; i ++) {
			if(i < 10) {
				new File(TMP_DIR_PATH.toString() + File.separatorChar + prefix + "000" + Integer.toString(i))
					.createNewFile();
			} else if(i < 100) {
				new File(TMP_DIR_PATH.toString() + File.separatorChar + prefix + "00" + Integer.toString(i))
					.createNewFile();
			} else if(i < 1000) {
				new File(TMP_DIR_PATH.toString() + File.separatorChar + prefix + "0" + Integer.toString(i))
					.createNewFile();
			}
		}

		final Random rnd = new Random();
		for(int i = 0; i < count; i ++) {
			new File(TMP_DIR_PATH.toString() + File.separatorChar + Integer.toString(rnd.nextInt()))
				.createNewFile();
		}

		List<MutableDataItem> items = FileStorageDriver._list(
			new BasicItemFactory<>(), TMP_DIR_PATH.toString(), prefix, 10, "yohoho0099", count
		);
		assertEquals(99, items.size());

		items = FileStorageDriver._list(
			new BasicItemFactory<>(), TMP_DIR_PATH.toString(), prefix, 10, null, 100
		);
		assertEquals(100, items.size());

		items = FileStorageDriver._list(
			new BasicItemFactory<>(), TMP_DIR_PATH.toString(), null, 10, null, 2 * count
		);
		assertEquals(2 * count, items.size());
	}
}