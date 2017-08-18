package com.emc.mongoose.common.io.collection;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 Created by andrey on 02.12.16.
 Not thread safe.
 */
public abstract class BufferingInputBase<T>
extends ListInput<T> {

	protected final int capacity;

	public BufferingInputBase(final int capacity) {
		super(new ArrayList<>(capacity));
		this.capacity = capacity;
	}

	private int loadMore()
	throws IOException {
		final T lastItem = size > 0 ? items.get(size - 1) : null;
		i = 0;
		items.clear();
		return size = loadMoreItems(lastItem);
	}

	/**
	 Called when the elements buffer is exhausted. Should put more (but not more than "capacity")
	 new elements into the empty buffer.
	 @return the count of the items was actually loaded into the "items" buffer or 0 if no more items are available.
	 */
	protected abstract int loadMoreItems(final T lastItem)
	throws IOException;

	@Override
	public final T get()
	throws IOException {
		if(i == size) {
			if(loadMore() <= 0) {
				throw new EOFException();
			}
		}
		return items.get(i ++);
	}

	@Override
	public final int get(final List<T> buffer, final int maxCount)
	throws IOException {
		int n = size - i;
		if(n == 0) {
			if(loadMore() <= 0) {
				throw new EOFException();
			}
		}
		n = Math.min(size - i, maxCount);
		for(final T item : items.subList(i, i + n)) {
			buffer.add(item);
		}
		i += n;
		return n;
	}

	@Override
	public void close()
	throws IOException {
		super.close();
		items.clear();
	}
}