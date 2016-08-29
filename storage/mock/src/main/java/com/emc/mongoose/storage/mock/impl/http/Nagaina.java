package com.emc.mongoose.storage.mock.impl.http;

import com.emc.mongoose.common.exception.OmgDoesNotPerformException;
import com.emc.mongoose.common.exception.OmgLookAtMyConsoleException;
import com.emc.mongoose.common.net.NetUtil;
import com.emc.mongoose.common.net.ssl.SslContext;
import com.emc.mongoose.model.api.data.ContentSource;
import com.emc.mongoose.storage.mock.api.MutableDataItemMock;
import com.emc.mongoose.storage.mock.api.StorageMock;
import com.emc.mongoose.storage.mock.distribution.Conversation;
import com.emc.mongoose.storage.mock.distribution.MDns;
import com.emc.mongoose.storage.mock.distribution.NodeListener;
import com.emc.mongoose.storage.mock.distribution.RemoteConversation;
import com.emc.mongoose.storage.mock.impl.base.BasicMutableDataItemMock;
import com.emc.mongoose.storage.mock.impl.base.StorageMockBase;
import com.emc.mongoose.storage.mock.impl.http.request.AtmosRequestHandler;
import com.emc.mongoose.storage.mock.impl.http.request.RequestHandlerBase;
import com.emc.mongoose.storage.mock.impl.http.request.S3RequestHandler;
import com.emc.mongoose.storage.mock.impl.http.request.SwiftRequestHandler;
import com.emc.mongoose.ui.config.Config;
import com.emc.mongoose.ui.config.Config.ItemConfig.NamingConfig;
import com.emc.mongoose.ui.config.Config.LoadConfig.LimitConfig;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.TimeUnit;

import static com.emc.mongoose.storage.mock.distribution.Conversation.SERVICE_NAME;
import static java.rmi.registry.Registry.REGISTRY_PORT;

/**
 Created on 11.07.16.
 */
public final class Nagaina
extends StorageMockBase<MutableDataItemMock>{

	private static final Logger LOG = LogManager.getLogger();

	private final int port;
	private final EventLoopGroup[] dispatchGroups;
	private final EventLoopGroup[] workGroups;
	private final Channel[] channels;
	private final RequestHandlerBase s3RequestHandler, swiftRequestHandler, atmosRequestHandler;
	private JmDNS jmDns;
	private NodeListener nodeListener;

	@SuppressWarnings("ConstantConditions")
	public Nagaina(
		final Config.StorageConfig storageConfig, final Config.LoadConfig loadConfig,
		final Config.ItemConfig itemConfig
	) {
		super(storageConfig.getMockConfig(), loadConfig.getMetricsConfig(), itemConfig);
		port = storageConfig.getPort();
		final int headCount = storageConfig.getMockConfig().getHeadCount();
		dispatchGroups = new EventLoopGroup[headCount];
		workGroups = new EventLoopGroup[headCount];
		channels = new Channel[headCount];
		LOG.info(Markers.MSG, "Starting with {} head(s)", headCount);
		final LimitConfig limitConfig = loadConfig.getLimitConfig();
		final NamingConfig namingConfig = itemConfig.getNamingConfig();
		final ContentSource contentSource = getContentSource();
		s3RequestHandler = new S3RequestHandler<>(
			limitConfig, namingConfig, this, contentSource
		);
		swiftRequestHandler = new SwiftRequestHandler<>(
			limitConfig, namingConfig, this, contentSource
		);
		atmosRequestHandler = new AtmosRequestHandler<>(
			limitConfig, namingConfig, this, contentSource
		);
		try {
			final ServiceInfo serviceInfo =
				ServiceInfo.create("_http._tcp.local.", "nagaina", port - 1, "storage mock");
			jmDns = JmDNS.create(NetUtil.getHostAddr());
			LOG.info("JmDNS address: " + jmDns.getInetAddress());
			jmDns.registerService(serviceInfo);
			LOG.info("Nagaina registered as service");
		} catch(final IOException | OmgDoesNotPerformException | OmgLookAtMyConsoleException e) {
			LogUtil.exception(
				LOG, Level.ERROR, e, "Failed to register Nagaina as service"
			);
		}
	}

	@Override
	protected void doStart()
	throws IllegalStateException {
		final int portsNumber = dispatchGroups.length;
		for(int i = 0; i < portsNumber; i++) {
			try {
				dispatchGroups[i] = new EpollEventLoopGroup(0, new DefaultThreadFactory("dispatcher-" + i));
				workGroups[i] = new EpollEventLoopGroup();
				final ServerBootstrap serverBootstrap = new ServerBootstrap();
				final int currentIndex = i;
				serverBootstrap.group(dispatchGroups[i], workGroups[i])
					.channel(EpollServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(final SocketChannel socketChannel)
						throws Exception {
							final ChannelPipeline pipeline = socketChannel.pipeline();
							if(currentIndex % 2 == 1) {
								pipeline.addLast(new SslHandler(SslContext.INSTANCE.createSSLEngine()));
							}
							pipeline.addLast(new HttpServerCodec());
							pipeline.addLast(swiftRequestHandler);
							pipeline.addLast(atmosRequestHandler);
							pipeline.addLast(s3RequestHandler);
						}
					});
				final ChannelFuture bind = serverBootstrap.bind(port + i);
				bind.sync();
				channels[i] = bind.sync().channel();
			} catch(final Exception e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to start the head at port #{}", port + i
				);
				throw new IllegalStateException();
			}
		}
		if(portsNumber > 1) {
			LOG.info(Markers.MSG, "Listening the ports {} .. {}",
				port, port + portsNumber - 1);
		} else {
			LOG.info(Markers.MSG, "Listening the port {}", port);
		}
		try {
			LOG.info(Markers.MSG, "Register RMI method");
			final Conversation conversation = new RemoteConversation();
			final Registry registry = LocateRegistry.createRegistry(REGISTRY_PORT);
			registry.rebind(SERVICE_NAME, conversation);
			nodeListener = new NodeListener(jmDns, MDns.Type.HTTP);
			nodeListener.open();
			LOG.info(Markers.MSG, "Discover nodes");
		} catch(final IOException e) {
			LogUtil.exception(
			LOG, Level.ERROR, e, "Failed to start node discovering"
			);
		}
	}

	@Override
	public boolean await()
	throws InterruptedException {
		return await(Long.MAX_VALUE, TimeUnit.DAYS);
	}

	@Override
	public boolean await(long timeout, final TimeUnit timeUnit)
	throws InterruptedException {
		for(final Channel channel : channels) {
			try {
				channel.closeFuture().sync();
			} catch(final InterruptedException e) {
				LOG.info(Markers.MSG, "Interrupting the Nagaina");
			}
		}
		return true;
	}

	@Override
	public void close()
	throws IOException {
		nodeListener.close();
		jmDns.unregisterAllServices();
		jmDns.close();
		for(final Channel channel: channels) {
			channel.close();
		}
	}

	@Override
	protected MutableDataItemMock newDataObject(final String id, final long offset, final long size) {
		return new BasicMutableDataItemMock(id, offset, size, 0, contentSrc);
	}
}
