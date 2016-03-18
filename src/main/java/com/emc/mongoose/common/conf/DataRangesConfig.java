package com.emc.mongoose.common.conf;
//
import java.util.ArrayList;
import java.util.List;
/**
 Created by andrey on 02.03.16.
 */
public class DataRangesConfig {
	//
	public final static class InvalidRangeException
	extends IllegalArgumentException {
		public InvalidRangeException(final String msg) {
			super(msg);
		}
	}
	//
	public final static class ByteRange {
		//
		private final long beg;
		private final long end;
		//
		public ByteRange(final String rawRange)
		throws InvalidRangeException {
			if(rawRange.contains("-")) {
				final String[] pair = rawRange.split("-");
				if(pair.length == 2) {
					try {
						beg = Long.parseLong(pair[0]);
						end = Long.parseLong(pair[1]);
					} catch(final NumberFormatException e) {
						throw new InvalidRangeException("Invalid range string: \""+ rawRange + "\"");
					}
					if(beg > end) {
						throw new InvalidRangeException("Invalid range string: \""+ rawRange + "\"");
					}
				} else {
					throw new InvalidRangeException("Invalid range string: \""+ rawRange + "\"");
				}
			} else {
				throw new InvalidRangeException("Invalid range string: \""+ rawRange + "\"");
			}
		}
		//
		public final long getBeg() {
			return beg;
		}
		//
		public final long getEnd() {
			return end;
		}
	}
	//
	private final int randomCount;
	private final List<ByteRange> fixedByteRanges;
	//
	public DataRangesConfig(final int randomCount) {
		this.randomCount = randomCount;
		this.fixedByteRanges = null;
	}
	//
	public DataRangesConfig(final String rawRangesConfig)
	throws InvalidRangeException {
		//
		randomCount = 0;
		//
		final String[] rawRanges;
		if(rawRangesConfig.contains(",")) {
			rawRanges = rawRangesConfig.split(",");
		} else {
			rawRanges = new String[] { rawRangesConfig };
		}
		//
		if(rawRanges.length > 0) {
			fixedByteRanges = new ArrayList<>();
			for(final String rawRange : rawRanges) {
				fixedByteRanges.add(new ByteRange(rawRange));
			}
		} else {
			fixedByteRanges = null;
		}
	}
	//
	public final int getRandomCount() {
		return randomCount;
	}
	//
	public final List<ByteRange> getFixedByteRanges() {
		return fixedByteRanges;
	}
}