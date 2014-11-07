package com.emc.mongoose.web.load.server.impl.type;
//
import com.emc.mongoose.base.data.persist.FrameBuffConsumer;
import com.emc.mongoose.base.load.server.ConsumerSvc;
import com.emc.mongoose.util.logging.MessageFactoryImpl;
import com.emc.mongoose.web.api.WSRequestConfig;
import com.emc.mongoose.web.data.WSObject;
import com.emc.mongoose.object.load.ObjectLoadExecutor;
import com.emc.mongoose.object.load.server.ObjectLoadSvc;
import com.emc.mongoose.web.load.impl.type.Create;
import com.emc.mongoose.util.conf.RunTimeConfig;
import com.emc.mongoose.util.logging.Markers;
import com.emc.mongoose.util.remote.RecordFrameBuffer;
import com.emc.mongoose.util.remote.Service;
import com.emc.mongoose.util.remote.ServiceUtils;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
/**
 Created by kurila on 30.05.14.
 */
public final class CreateSvc<T extends WSObject>
extends Create<T>
implements ObjectLoadSvc<T> {
	//
	private final Logger log;
	//
	public CreateSvc(
		final RunTimeConfig runTimeConfig,
		final String[] addrs, final WSRequestConfig<T> reqConf, final long maxCount,
		final int threadsPerNode, final long minObjSize, final long maxObjSize
	)
	throws IOException, CloneNotSupportedException {
		super(runTimeConfig, addrs, reqConf, maxCount, threadsPerNode, null, minObjSize, maxObjSize);
		log = LogManager.getLogger(new MessageFactoryImpl(runTimeConfig));
		// by default, may be overriden later externally:
		super.setConsumer(new FrameBuffConsumer<T>());
	}
	//
	@Override
	public final synchronized void close()
	throws IOException {
		super.close();
		// close the exposed network service, if any
		final Service svc = ServiceUtils.getLocalSvc(getName());
		if(svc==null) {
			log.debug(Markers.MSG, "The load was not exposed remotely");
		} else {
			log.debug(Markers.MSG, "The load was exposed remotely, removing the service");
			ServiceUtils.close(svc);
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final void setConsumer(final ConsumerSvc<T> consumer) {
		this.consumer = consumer;
		log.debug(Markers.MSG, "Trying to resolve local service from the name");
		try {
			final ConsumerSvc remoteSvc = ConsumerSvc.class.cast(consumer);
			final String remoteSvcName = remoteSvc.getName();
			log.debug(Markers.MSG, "Name is {}", remoteSvcName);
			final Service localSvc = ServiceUtils.getLocalSvc(remoteSvcName);
			if(localSvc==null) {
				log.error(Markers.ERR, "Failed to get local service for name {}", remoteSvcName);
			} else {
				super.setConsumer((ObjectLoadExecutor<T>) localSvc);
			}
			log.debug(Markers.MSG, "Successfully resolved local service and appended it as consumer");
		} catch(final IOException ee) {
			log.error(Markers.ERR, "Looks like network failure", ee);
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final List<T> takeFrame()
	throws RemoteException {
		List<T> recFrame = Collections.emptyList();
		if(RecordFrameBuffer.class.isInstance(consumer)) {
			recFrame = ((RecordFrameBuffer<T>) consumer).takeFrame();
		}
		return recFrame;
	}
	//
}
