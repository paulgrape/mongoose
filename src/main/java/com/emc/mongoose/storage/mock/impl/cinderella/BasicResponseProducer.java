package com.emc.mongoose.storage.mock.impl.cinderella;
//
import com.emc.mongoose.common.io.HTTPOutputStream;
import com.emc.mongoose.common.logging.LogUtil;
//
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.EntityAsyncContentProducer;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.io.OutputStream;
/**
 * Created by olga on 12.02.15.
 */
public final class BasicResponseProducer
extends BasicAsyncResponseProducer {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final HttpResponse response;
	private final HttpAsyncContentProducer producer;
	//
	public BasicResponseProducer(HttpResponse response) {
		super(response);
		this.response = response;
		final HttpEntity entity = response.getEntity();
		if (entity != null) {
			if (entity instanceof HttpAsyncContentProducer) {
				this.producer = (HttpAsyncContentProducer) entity;
			} else {
				this.producer = new EntityAsyncContentProducer(entity);
			}
		} else {
			this.producer = null;
		}
	}
	//
	@Override
	public final void produceContent(
		final ContentEncoder encoder, final IOControl ioctrl)
	throws IOException {
		try(final OutputStream outStream = HTTPOutputStream.getInstance(encoder, ioctrl)) {
			final HttpEntity entity = this.response.getEntity();
			if( entity != null) {
				if(LOG.isTraceEnabled(LogUtil.MSG)) {
					LOG.trace(
						LogUtil.MSG, "Write out {} bytes",
						entity.getContentLength()
					);
				}
				entity.writeTo(outStream);
			}
		} catch(final InterruptedException e) {
			// do nothing
		} finally {
			this.producer.close();
		}
	}
	//
	@Override
	public final void close()
	throws IOException {
		if (this.producer != null) {
			this.producer.close();
		}
	}
	//
	@Override
	public final String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(this.response);
		if (this.producer != null) {
			buf.append(" ").append(this.producer);
		}
		return buf.toString();
	}
}
