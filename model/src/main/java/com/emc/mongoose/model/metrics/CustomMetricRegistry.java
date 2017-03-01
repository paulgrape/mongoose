package com.emc.mongoose.model.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A registry of metric instances.
 */
public final class CustomMetricRegistry
implements Closeable, MetricSet {

	/**
	 * Concatenates elements to form a dotted name, eliding any null values or empty strings.
	 *
	 * @param name     the first element of the name
	 * @param names    the remaining elements of the name
	 * @return {@code name} and {@code names} concatenated by periods
	 */
	public static String name(final String name, final String... names) {
		final StringBuilder builder = new StringBuilder();
		append(builder, name);
		if(names != null) {
			for(String s : names) {
				append(builder, s);
			}
		}
		return builder.toString();
	}

	/**
	 * Concatenates a class name and elements to form a dotted name, eliding any null values or
	 * empty strings.
	 *
	 * @param klass    the first element of the name
	 * @param names    the remaining elements of the name
	 * @return {@code klass} and {@code names} concatenated by periods
	 */
	public static String name(final Class<?> klass, final String... names) {
		return name(klass.getName(), names);
	}

	private static void append(StringBuilder builder, String part) {
		if(part != null && !part.isEmpty()) {
			if(builder.length() > 0) {
				builder.append('.');
			}
			builder.append(part);
		}
	}

	private final ConcurrentMap<String, Metric> metrics;
	private final List<MetricRegistryListener> listeners;

	/**
	 * Creates a new {@link CustomMetricRegistry}.
	 */
	public CustomMetricRegistry() {
		this.metrics = buildMap();
		this.listeners = new CopyOnWriteArrayList<MetricRegistryListener>();
	}

	/**
	 * Creates a new {@link ConcurrentMap} implementation for use inside the registry. Override this
	 * to create a {@link CustomMetricRegistry} with space- or time-bounded metric lifecycles, for
	 * example.
	 *
	 * @return a new {@link ConcurrentMap}
	 */
	private ConcurrentMap<String, Metric> buildMap() {
		return new ConcurrentHashMap<>();
	}

	/**
	 * Given a {@link Metric}, registers it under the given name.
	 *
	 * @param name   the name of the metric
	 * @param metric the metric
	 * @param <T>    the type of the metric
	 * @return {@code metric}
	 * @throws IllegalArgumentException if the name is already registered
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Metric> T register(final String name, final T metric)
	throws IllegalArgumentException {
		if(metric instanceof MetricSet) {
			registerAll(name, (MetricSet) metric);
		} else {
			final Metric existing = metrics.putIfAbsent(name, metric);
			if(existing == null) {
				onMetricAdded(name, metric);
			} else {
				throw new IllegalArgumentException("A metric named " + name + " already exists");
			}
		}
		return metric;
	}

	/**
	 * Given a metric set, registers them.
	 *
	 * @param metrics    a set of metrics
	 * @throws IllegalArgumentException if any of the names are already registered
	 */
	public final void registerAll(final MetricSet metrics) throws IllegalArgumentException {
		registerAll(null, metrics);
	}

	/**
	 * Return the {@link Counter} registered under this name; or create and register
	 * a new {@link Counter} if none is registered.
	 *
	 * @param name the name of the metric
	 * @return a new or pre-existing {@link Counter}
	 */
	public final Counter counter(final String name) {
		return getOrAdd(name, MetricBuilder.COUNTERS);
	}

	/**
	 * Return the {@link Histogram} registered under this name; or create and register
	 * a new {@link Histogram} if none is registered.
	 *
	 * @param name the name of the metric
	 * @return a new or pre-existing {@link Histogram}
	 */
	public final Histogram histogram(final String name) {
		return getOrAdd(name, MetricBuilder.HISTOGRAMS);
	}

	/**
	 * Return the {@link Meter} registered under this name; or create and register
	 * a new {@link Meter} if none is registered.
	 *
	 * @param name the name of the metric
	 * @return a new or pre-existing {@link Meter}
	 */
	public final Meter meter(final String name) {
		return getOrAdd(name, MetricBuilder.METERS);
	}

	/**
	 * Return the {@link Timer} registered under this name; or create and register
	 * a new {@link Timer} if none is registered.
	 *
	 * @param name the name of the metric
	 * @return a new or pre-existing {@link Timer}
	 */
	public final Timer timer(final String name) {
		return getOrAdd(name, MetricBuilder.TIMERS);
	}

	/**
	 * Removes the metric with the given name.
	 *
	 * @param name the name of the metric
	 * @return whether or not the metric was removed
	 */
	public final boolean remove(final String name) {
		final Metric metric = metrics.remove(name);
		if(metric != null) {
			onMetricRemoved(name, metric);
			return true;
		}
		return false;
	}

	/**
	 * Removes all metrics which match the given filter.
	 *
	 * @param filter a filter
	 */
	public final void removeMatching(final MetricFilter filter) {
		for(Map.Entry<String, Metric> entry : metrics.entrySet()) {
			if(filter.matches(entry.getKey(), entry.getValue())) {
				remove(entry.getKey());
			}
		}
	}

	/**
	 * Adds a {@link MetricRegistryListener} to a collection of listeners that will be notified on
	 * metric creation.  Listeners will be notified in the order in which they are added.
	 * <p>
	 * <b>N.B.:</b> The listener will be notified of all existing metrics when it first registers.
	 *
	 * @param listener the listener that will be notified
	 */
	public final void addListener(final MetricRegistryListener listener) {
		listeners.add(listener);

		for(Map.Entry<String, Metric> entry : metrics.entrySet()) {
			notifyListenerOfAddedMetric(listener, entry.getValue(), entry.getKey());
		}
	}

	/**
	 * Removes a {@link MetricRegistryListener} from this registry's collection of listeners.
	 *
	 * @param listener the listener that will be removed
	 */
	public final void removeListener(final MetricRegistryListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Returns a set of the names of all the metrics in the registry.
	 *
	 * @return the names of all the metrics
	 */
	public final SortedSet<String> getNames() {
		return Collections.unmodifiableSortedSet(new TreeSet<String>(metrics.keySet()));
	}

	/**
	 * Returns a map of all the gauges in the registry and their names.
	 *
	 * @return all the gauges in the registry
	 */
	public final SortedMap<String, Gauge> getGauges() {
		return getGauges(MetricFilter.ALL);
	}

	/**
	 * Returns a map of all the gauges in the registry and their names which match the given filter.
	 *
	 * @param filter    the metric filter to match
	 * @return all the gauges in the registry
	 */
	public final SortedMap<String, Gauge> getGauges(final MetricFilter filter) {
		return getMetrics(Gauge.class, filter);
	}

	/**
	 * Returns a map of all the counters in the registry and their names.
	 *
	 * @return all the counters in the registry
	 */
	public final SortedMap<String, Counter> getCounters() {
		return getCounters(MetricFilter.ALL);
	}

	/**
	 * Returns a map of all the counters in the registry and their names which match the given
	 * filter.
	 *
	 * @param filter    the metric filter to match
	 * @return all the counters in the registry
	 */
	public final SortedMap<String, Counter> getCounters(final MetricFilter filter) {
		return getMetrics(Counter.class, filter);
	}

	/**
	 * Returns a map of all the histograms in the registry and their names.
	 *
	 * @return all the histograms in the registry
	 */
	public final SortedMap<String, Histogram> getHistograms() {
		return getHistograms(MetricFilter.ALL);
	}

	/**
	 * Returns a map of all the histograms in the registry and their names which match the given
	 * filter.
	 *
	 * @param filter    the metric filter to match
	 * @return all the histograms in the registry
	 */
	public final SortedMap<String, Histogram> getHistograms(final MetricFilter filter) {
		return getMetrics(Histogram.class, filter);
	}

	/**
	 * Returns a map of all the meters in the registry and their names.
	 *
	 * @return all the meters in the registry
	 */
	public final SortedMap<String, Meter> getMeters() {
		return getMeters(MetricFilter.ALL);
	}

	/**
	 * Returns a map of all the meters in the registry and their names which match the given filter.
	 *
	 * @param filter    the metric filter to match
	 * @return all the meters in the registry
	 */
	public final SortedMap<String, Meter> getMeters(final MetricFilter filter) {
		return getMetrics(Meter.class, filter);
	}

	/**
	 * Returns a map of all the timers in the registry and their names.
	 *
	 * @return all the timers in the registry
	 */
	public final SortedMap<String, Timer> getTimers() {
		return getTimers(MetricFilter.ALL);
	}

	/**
	 * Returns a map of all the timers in the registry and their names which match the given filter.
	 *
	 * @param filter    the metric filter to match
	 * @return all the timers in the registry
	 */
	public final SortedMap<String, Timer> getTimers(final MetricFilter filter) {
		return getMetrics(Timer.class, filter);
	}

	@SuppressWarnings("unchecked")
	private <T extends Metric> T getOrAdd(final String name, final MetricBuilder<T> builder) {
		final Metric metric = metrics.get(name);
		if(builder.isInstance(metric)) {
			return (T) metric;
		} else if(metric == null) {
			try {
				return register(name, builder.newMetric());
			} catch (IllegalArgumentException e) {
				final Metric added = metrics.get(name);
				if(builder.isInstance(added)) {
					return (T) added;
				}
			}
		}
		throw new IllegalArgumentException(name + " is already used for a different type of metric");
	}

	@SuppressWarnings("unchecked")
	private <T extends Metric> SortedMap<String, T> getMetrics(
		final Class<T> klass, final MetricFilter filter
	) {
		final TreeMap<String, T> timers = new TreeMap<String, T>();
		for(final Map.Entry<String, Metric> entry : metrics.entrySet()) {
			if(klass.isInstance(entry.getValue()) && filter.matches(entry.getKey(),
				entry.getValue())) {
				timers.put(entry.getKey(), (T) entry.getValue());
			}
		}
		return Collections.unmodifiableSortedMap(timers);
	}

	private void onMetricAdded(final String name, final Metric metric) {
		for(final MetricRegistryListener listener : listeners) {
			notifyListenerOfAddedMetric(listener, metric, name);
		}
	}

	private void notifyListenerOfAddedMetric(
		final MetricRegistryListener listener, final Metric metric, final String name
	) {
		if(metric instanceof Gauge) {
			listener.onGaugeAdded(name, (Gauge<?>) metric);
		} else if(metric instanceof Counter) {
			listener.onCounterAdded(name, (Counter) metric);
		} else if(metric instanceof Histogram) {
			listener.onHistogramAdded(name, (Histogram) metric);
		} else if(metric instanceof Meter) {
			listener.onMeterAdded(name, (Meter) metric);
		} else if(metric instanceof Timer) {
			listener.onTimerAdded(name, (Timer) metric);
		} else {
			throw new IllegalArgumentException("Unsupported metric type: " + metric.getClass());
		}
	}

	private void onMetricRemoved(final String name, final Metric metric) {
		for(final MetricRegistryListener listener : listeners) {
			notifyListenerOfRemovedMetric(name, metric, listener);
		}
	}

	private void notifyListenerOfRemovedMetric(
		final String name, final Metric metric, final MetricRegistryListener listener
	) {
		if(metric instanceof Gauge) {
			listener.onGaugeRemoved(name);
		} else if(metric instanceof Counter) {
			listener.onCounterRemoved(name);
		} else if(metric instanceof Histogram) {
			listener.onHistogramRemoved(name);
		} else if(metric instanceof Meter) {
			listener.onMeterRemoved(name);
		} else if(metric instanceof Timer) {
			listener.onTimerRemoved(name);
		} else {
			throw new IllegalArgumentException("Unsupported metric type: " + metric.getClass());
		}
	}

	private void registerAll(final String prefix, final MetricSet metrics)
	throws IllegalArgumentException {
		for(final Map.Entry<String, Metric> entry : metrics.getMetrics().entrySet()) {
			if(entry.getValue() instanceof MetricSet) {
				registerAll(name(prefix, entry.getKey()), (MetricSet) entry.getValue());
			} else {
				register(name(prefix, entry.getKey()), entry.getValue());
			}
		}
	}

	@Override
	public final Map<String, Metric> getMetrics() {
		return Collections.unmodifiableMap(metrics);
	}

	@Override
	public final void close()
	throws IOException {
		metrics.clear();
		listeners.clear();
	}

	/**
	 * A quick and easy way of capturing the notion of default metrics.
	 */
	private interface MetricBuilder<T extends Metric> {
		MetricBuilder<Counter> COUNTERS = new MetricBuilder<Counter>() {
			@Override
			public Counter newMetric() {
				return new Counter();
			}

			@Override
			public boolean isInstance(final Metric metric) {
				return Counter.class.isInstance(metric);
			}
		};

		MetricBuilder<Histogram> HISTOGRAMS = new MetricBuilder<Histogram>() {
			@Override
			public Histogram newMetric() {
				return new Histogram(new ExponentiallyDecayingReservoir());
			}

			@Override
			public boolean isInstance(final Metric metric) {
				return Histogram.class.isInstance(metric);
			}
		};

		MetricBuilder<Meter> METERS = new MetricBuilder<Meter>() {
			@Override
			public Meter newMetric() {
				return new Meter();
			}

			@Override
			public boolean isInstance(final Metric metric) {
				return Meter.class.isInstance(metric);
			}
		};

		MetricBuilder<Timer> TIMERS = new MetricBuilder<Timer>() {
			@Override
			public Timer newMetric() {
				return new Timer();
			}

			@Override
			public boolean isInstance(final Metric metric) {
				return Timer.class.isInstance(metric);
			}
		};

		T newMetric();

		boolean isInstance(final Metric metric);
	}
}
