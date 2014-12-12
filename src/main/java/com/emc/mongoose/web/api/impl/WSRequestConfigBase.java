package com.emc.mongoose.web.api.impl;
//
import com.emc.mongoose.base.api.AsyncIOTask;
import com.emc.mongoose.base.api.impl.RequestConfigBase;
import com.emc.mongoose.base.data.DataSource;
import com.emc.mongoose.base.data.impl.DataRanges;
import com.emc.mongoose.util.pool.BasicInstancePool;
import com.emc.mongoose.web.api.MutableHTTPRequest;
import com.emc.mongoose.web.api.WSIOTask;
import com.emc.mongoose.web.api.WSRequestConfig;
import com.emc.mongoose.web.data.WSObject;
import com.emc.mongoose.run.Main;
import com.emc.mongoose.util.conf.RunTimeConfig;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.commons.codec.binary.Base64;
//
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.IOControl;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
/**
 Created by kurila on 09.06.14.
 */
public abstract class WSRequestConfigBase<T extends WSObject>
extends RequestConfigBase<T>
implements WSRequestConfig<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public final static long serialVersionUID = 42L;
	protected final String userAgent, signMethod;
	//
	public static WSRequestConfigBase getInstance() {
		return newInstanceFor(Main.RUN_TIME_CONFIG.get().getStorageApi());
	}
	//
	private final static String NAME_CLS_IMPL = "RequestConfig";
	//
	@SuppressWarnings("unchecked")
	public static WSRequestConfigBase newInstanceFor(final String api) {
		WSRequestConfigBase reqConf = null;
		final String apiImplClsFQN =
			WSRequestConfigBase.class.getPackage().getName() +
				Main.DOT + REL_PKG_PROVIDERS + Main.DOT +
				api.toLowerCase() + Main.DOT + NAME_CLS_IMPL;
		try {
			final Class apiImplCls = Class.forName(apiImplClsFQN);
			final Constructor<WSRequestConfigBase>
				constructor = (Constructor<WSRequestConfigBase>) apiImplCls.getConstructors()[0];
			reqConf = constructor.newInstance();
		} catch(final ClassNotFoundException e) {
			LOG.fatal(Markers.ERR, "API implementation not found: \"{}\"", apiImplClsFQN);
		} catch(final ClassCastException e) {
			LOG.fatal(Markers.ERR, "Class \"{}\" is not valid API implementation", apiImplClsFQN);
		} catch(final Exception e) {
			ExceptionHandler.trace(LOG, Level.FATAL, e, "WS API config instantiation failure");
		}
		return reqConf;
	}
	//
	protected ConcurrentHashMap<String, String> sharedHeadersMap;
	protected final Mac mac;
	//
	public WSRequestConfigBase()
	throws NoSuchAlgorithmException {
		this(null);
	}
	//
	@SuppressWarnings("unchecked")
	protected WSRequestConfigBase(final WSRequestConfig<T> reqConf2Clone)
	throws NoSuchAlgorithmException {
		super(reqConf2Clone);
		//
		signMethod = runTimeConfig.getHttpSignMethod();
		mac = Mac.getInstance(signMethod);
		final String
			runName = runTimeConfig.getRunName(),
			runVersion = runTimeConfig.getRunVersion(),
			contentType = runTimeConfig.getHttpContentType();
		userAgent = runName + '/' + runVersion;
		try {
			sharedHeadersMap = new ConcurrentHashMap<String, String>() {
				{
					put(HttpHeaders.USER_AGENT, userAgent);
					put(HttpHeaders.CONNECTION, VALUE_KEEP_ALIVE);
					put(HttpHeaders.CONTENT_TYPE, contentType);
				}
			};
			if(reqConf2Clone != null) {
				this
					.setSecret(reqConf2Clone.getSecret())
					.setScheme(reqConf2Clone.getScheme());
			}
			//
			final String pkgSpec = getClass().getPackage().getName();
			setAPI(pkgSpec.substring(pkgSpec.lastIndexOf('.') + 1));
		} catch(final Exception e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Request config instantiation failure");
		}
	}
	//
	@Override
	public MutableHTTPRequest createRequest() {
		MutableHTTPRequest r = null;
		switch(loadType) {
			case READ:
				r = WSIOTask.HTTPMethod.GET.createRequest();
				break;
			case DELETE:
				r = WSIOTask.HTTPMethod.DELETE.createRequest();
				break;
			case APPEND:
			case CREATE:
			case UPDATE:
				r = WSIOTask.HTTPMethod.PUT.createRequest();
				break;
		}
		return r;
	}
	//
	@Override
	public final WSRequestConfigBase<T> setAPI(final String api) {
		super.setAPI(api);
		return this;
	}
	//
	@Override
	public final WSRequestConfigBase<T> setDataSource(final DataSource<T> dataSrc) {
		super.setDataSource(dataSrc);
		return this;
	}
	//
	@Override
	public WSRequestConfigBase<T> setUserName(final String userName) {
		super.setUserName(userName);
		return this;
	}
	//
	@Override
	public final WSRequestConfigBase<T> setRetries(final boolean retryFlag) {
		super.setRetries(retryFlag);
		return this;
	}
	//
	@Override
	public final WSRequestConfigBase<T> setLoadType(final AsyncIOTask.Type loadType) {
		super.setLoadType(loadType);
		return this;
	}
	//
	public final String getNameSpace() {
		return sharedHeadersMap.get(KEY_EMC_NS);
	}
	public final WSRequestConfigBase<T> setNameSpace(final String nameSpace) {
		if(nameSpace==null) {
			LOG.debug(Markers.MSG, "Using empty namespace");
		} else {
			sharedHeadersMap.put(KEY_EMC_NS, nameSpace);
		}
		return this;
	}
	//
	@Override
	public WSRequestConfigBase<T> setProperties(final RunTimeConfig runTimeConfig) {
		//
		String paramName = "storage.scheme";
		try {
			setScheme(this.runTimeConfig.getStorageProto());
		} catch(final NoSuchElementException e) {
			LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		}
		//
		paramName = "data.namespace";
		try {
			setNameSpace(this.runTimeConfig.getDataNameSpace());
		} catch(final NoSuchElementException e) {
			LOG.debug(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		} catch(final IllegalStateException e) {
			LOG.debug(Markers.ERR, "Failed to set the namespace", e);
		}
		//
		super.setProperties(runTimeConfig);
		//
		return this;
	}
	//
	@Override
	public WSRequestConfigBase<T> setSecret(final String secret) {
		SecretKeySpec keySpec;
		LOG.trace(Markers.MSG, "Applying secret key {}", secret);
		try {
			keySpec = new SecretKeySpec(secret.getBytes(DEFAULT_ENC), signMethod);
			synchronized(mac) {
				mac.init(keySpec);
			}
		} catch(UnsupportedEncodingException e) {
			LOG.fatal(Markers.ERR, "Configuration error", e);
		} catch(InvalidKeyException e) {
			LOG.error(Markers.ERR, "Invalid secret key", e);
		}
		//
		super.setSecret(secret);
		//
		return this;
	}
	//
	@Override
	public final List<Header> getSharedHeaders() {
		final LinkedList<Header> headers = new LinkedList<>();
		for(final String headerName: sharedHeadersMap.keySet()) {
			headers.add(new BasicHeader(headerName, sharedHeadersMap.get(headerName)));
		}
		return headers;
	}
	//
	@Override
	public final Map<String, String> getSharedHeadersMap() {
		return sharedHeadersMap;
	}
	//
	@Override
	public final String getUserAgent() {
		return userAgent;
	}
	//
	@Override
	public final WSIOTask<T> getRequestFor(final T dataItem) {
		WSIOTask<T> ioTask;
		if(dataItem == null) {
			LOG.debug(Markers.MSG, "Preparing poison request");
			ioTask = (WSIOTask<T>) BasicWSIOTask.POISON;
		} else {
			BasicInstancePool pool;
			synchronized(AsyncIOTask.POOL_MAP) {
				if(AsyncIOTask.POOL_MAP.containsKey(this)) {
					pool = AsyncIOTask.POOL_MAP.get(this);
				} else {
					pool = new BasicInstancePool<>(BasicWSIOTask.class);
					AsyncIOTask.POOL_MAP.put(this, pool);
				}
			}
			ioTask = BasicWSIOTask.class.cast(pool.take())
				.setRequestConfig(this)
				.setDataItem(dataItem);
		}
		return ioTask;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public void readExternal(final ObjectInput in)
	throws IOException, ClassNotFoundException {
		super.readExternal(in);
		sharedHeadersMap = (ConcurrentHashMap<String,String>) in.readObject();
		/*final int headersCount = in.readInt();
		sharedHeadersMap = new ConcurrentHashMap<>(headersCount);
		LOG.trace(Markers.MSG, "Got headers count {}", headersCount);
		String key, value;
		for(int i = 0; i < headersCount; i ++) {
			key = String.class.cast(in.readObject());
			LOG.trace(Markers.MSG, "Got header key {}", key);
			value = String.class.cast(in.readObject());
			LOG.trace(Markers.MSG, "Got header value {}", value);
			sharedHeadersMap.put(key, value);
		}*/
		LOG.trace(Markers.MSG, "Got headers map {}", sharedHeadersMap);
	}
	//
	@Override
	public void writeExternal(final ObjectOutput out)
	throws IOException {
		super.writeExternal(out);
		out.writeObject(sharedHeadersMap);
		/*out.writeInt(sharedHeadersMap.size());
		for(final String key: sharedHeadersMap.keySet()) {
			out.writeObject(key);
			out.writeObject(sharedHeadersMap.get(key));
		}*/
	}
	//
	protected abstract void applyObjectId(final T dataItem, final HttpResponse httpResponse);
	//
	@Override
	public final void applyDataItem(final MutableHTTPRequest httpRequest, final T dataItem)
	throws IllegalStateException, URISyntaxException {
		applyObjectId(dataItem, null);
		applyURI(httpRequest, dataItem);
		switch(loadType) {
			case CREATE:
				applyPayLoad(httpRequest, dataItem);
				break;
			case UPDATE:
				applyRangesHeaders(httpRequest, dataItem);
				applyPayLoad(httpRequest, dataItem.getPendingUpdatesContentEntity());
				break;
			case APPEND:
				applyAppendRangeHeader(httpRequest, dataItem);
				applyPayLoad(httpRequest, dataItem.getPendingAugmentContentEntity());
				break;
		}
	}
	//
	@Override
	public final void applyHeadersFinally(final MutableHTTPRequest httpRequest) {
		applyDateHeader(httpRequest);
		applyAuthHeader(httpRequest);
		if(LOG.isTraceEnabled(Markers.MSG)) {
			synchronized(LOG) {
				LOG.trace(
					Markers.MSG, "built request: {} {}",
					httpRequest.getRequestLine().getMethod(), httpRequest.getRequestLine().getUri()
				);
				for(final Header header: httpRequest.getAllHeaders()) {
					LOG.trace(Markers.MSG, "\t{}: {}", header.getName(), header.getValue());
				}
				if(httpRequest.getClass().isInstance(HttpEntityEnclosingRequest.class)) {
					LOG.trace(
						Markers.MSG, "\tcontent: {} bytes",
						HttpEntityEnclosingRequest.class.cast(
							httpRequest
						).getEntity().getContentLength()
					);
				} else {
					LOG.trace(Markers.MSG, "\t---- no content ----");
				}
			}
		}
	}
	//
	protected abstract void applyURI(final MutableHTTPRequest httpRequest, final T dataItem)
	throws IllegalArgumentException, URISyntaxException;
	//
	protected final void applyPayLoad(
		final MutableHTTPRequest httpRequest, final HttpEntity httpEntity
	) {
		httpRequest.setEntity(httpEntity);
	}
	// merge subsequent updated ranges functionality is here
	protected final void applyRangesHeaders(final MutableHTTPRequest httpRequest, final T dataItem) {
		httpRequest.removeHeaders(HttpHeaders.RANGE); // cleanup
		long rangeBeg = -1, rangeEnd = -1, rangeLen;
		int rangeCount = dataItem.getCountRangesTotal();
		for(int i = 0; i < rangeCount; i++) {
			rangeLen = dataItem.getRangeSize(i);
			if(dataItem.isRangeUpdatePending(i)) {
				LOG.trace(Markers.MSG, "\"{}\": should update range #{}", dataItem, i);
				if(rangeBeg < 0) { // begin of the possible updated ranges sequence
					rangeBeg = DataRanges.getRangeOffset(i);
					rangeEnd = rangeBeg + rangeLen - 1;
					LOG.trace(
						Markers.MSG, "Begin of the possible updated ranges sequence @{}", rangeBeg
					);
				} else if(rangeEnd > 0) { // next range in the sequence of updated ranges
					rangeEnd += rangeLen;
				}
				if(i == rangeCount - 1) { // this is the last range which is updated also
					LOG.trace(Markers.MSG, "End of the updated ranges sequence @{}", rangeEnd);
					httpRequest.addHeader(
						HttpHeaders.RANGE, String.format(MSG_TMPL_RANGE_BYTES, rangeBeg, rangeEnd)
					);
				}
			} else if(rangeBeg > -1 && rangeEnd > -1) { // end of the updated ranges sequence
				LOG.trace(Markers.MSG, "End of the updated ranges sequence @{}", rangeEnd);
				httpRequest.addHeader(
					HttpHeaders.RANGE, String.format(MSG_TMPL_RANGE_BYTES, rangeBeg, rangeEnd)
				);
				// drop the updated ranges sequence info
				rangeBeg = -1;
				rangeEnd = -1;
			}
		}
	}
	//
	protected final void applyAppendRangeHeader(
		final MutableHTTPRequest httpRequest, final T dataItem
	) {
		httpRequest.addHeader(
			HttpHeaders.RANGE,
			String.format(MSG_TMPL_RANGE_BYTES_APPEND, dataItem.getSize())
		);
	}
	//
	private final static DateFormat FMT_DATE_RFC1123 = new SimpleDateFormat(
		"EEE, dd MMM yyyy HH:mm:ss zzz", Main.LOCALE_DEFAULT
	) {
		{ setTimeZone(Main.TZ_UTC); }
	};
	//
	protected void applyDateHeader(final MutableHTTPRequest httpRequest) {
		httpRequest.setHeader(
			HttpHeaders.DATE, FMT_DATE_RFC1123.format(Main.CALENDAR_DEFAULT.getTime())
		);
	}
	//
	protected abstract void applyAuthHeader(final MutableHTTPRequest httpRequest);
	//
	//@Override
	//public final int hashCode() {
	//	return uriBuilder.hashCode()^mac.hashCode()^api.hashCode();
	//}
	//
	@Override
	public String getSignature(final String canonicalForm) {
		byte[] signature = null;
		try {
			synchronized(mac) {
				signature = mac.doFinal(canonicalForm.getBytes(DEFAULT_ENC));
			}
		} catch(UnsupportedEncodingException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to calculate the signature");
		}
		final String signature64 = signature == null ? null : Base64.encodeBase64String(signature);
		LOG.trace(Markers.MSG, "Calculated signature: \"{}\"", signature64);
		return signature64;
	}
	//
	@Override
	public void receiveResponse(final HttpResponse response, final T dataItem) {
		// do nothing
	}
	//
	@Override
	public final boolean consumeContent(
		final InputStream contentStream, final IOControl ioCtl, T dataItem
	) throws IOException {
		boolean ok = false;
		if(dataItem != null) {
			if(loadType == AsyncIOTask.Type.READ) { // read
				if(verifyContentFlag) { // read and do verify
					if(dataItem.compareWith(contentStream)) {
						ok = true;
					}
				} else { // read, verification is disabled - consume quetly
					consumeContentQuetly(contentStream, ioCtl);
				}
			} else { // append | create | delete | update - consume quetly
				consumeContentQuetly(contentStream, ioCtl);
			}
		} else { // poison or special request (e.g. bucket-related)? - consume quetly
			consumeContentQuetly(contentStream, ioCtl);
		}
		return ok;
	}
	//
	@SuppressWarnings("StatementWithEmptyBody")
	private void consumeContentQuetly(final InputStream contentStream, final IOControl ioCtl)
	throws IOException {
		final byte buff[] = new byte[(int) runTimeConfig.getDataPageSize()];
		while(contentStream.read(buff) != -1);
	}
}
