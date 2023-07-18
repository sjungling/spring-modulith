/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events;

import static org.springframework.core.annotation.AnnotatedElementUtils.*;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.modulith.Externalized;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
public class DefaultEventExternalizationConfiguration implements EventExternalizationConfiguration {

	private static final Predicate<Object> DEFAULT_FILTER = it -> true;
	private static final Function<Object, String> DEFAULT_ROUTER = it -> Optional.of(it)
			.flatMap(byExternalizedAnnotations())
			.orElseGet(() -> byFullyQualifiedTypeName().apply(it));

	private final Predicate<Object> filter;
	private final Function<Object, Object> mapper;
	private final Function<Object, String> router;

	static {

	}

	/**
	 * Creates a new {@link DefaultEventExternalizationConfiguration}
	 *
	 * @param filter must not be {@literal null}.
	 * @param mapper must not be {@literal null}.
	 * @param router must not be {@literal null}.
	 */
	DefaultEventExternalizationConfiguration(Predicate<Object> filter, Function<Object, Object> mapper,
			Function<Object, String> router) {

		this.filter = filter;
		this.mapper = mapper;
		this.router = router;
	}

	/**
	 * Creates a default {@link DefaultEventExternalizationConfiguration} with the following characteristics:
	 * <ul>
	 * <li>Only events that reside in any application auto-configuration package and are annotated with
	 * {@link Externalized} will be selected for externalization.</li>
	 * <li>Routing information is discovered from the {@link Externalized} annotation and, if missing, will default to the
	 * application-local name of the event type. In other words, an event type {@code com.acme.myapp.mymodule.MyEvent}
	 * will result in a route {@code mymodule.MyEvent}.</li>
	 * </ul>
	 *
	 * @param packages must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @see Externalized
	 */
	public static DefaultEventExternalizationConfiguration defaults(Collection<String> packages) {

		Assert.notEmpty(packages, "Packages must not be null or empty!");

		Function<Object, String> router = it -> Optional.of(it)
				.flatMap(byExternalizedAnnotations())
				.or(() -> byApplicationLocalName(packages).apply(it))
				.orElseGet(() -> byFullyQualifiedTypeName().apply(it));

		return DefaultEventExternalizationConfiguration.builder()
				.selectByPackagesAndAnnotation(packages, AnnotationTargetLookup::hasExternalizedAnnotation)
				.route(router);
	}

	public static Selector builder() {
		return new Selector();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.foo.EventExternalizerFilter#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(Object event) {
		return filter.test(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.foo.EventExternalizationConfiguration#map(java.lang.Object)
	 */
	@Override
	public Object map(Object event) {
		return mapper.apply(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.foo.EventExternalizerFilter#determineTarget(java.lang.Object)
	 */
	@Override
	public RoutingTarget determineTarget(Object event) {
		return new RoutingTarget(router.apply(event));
	}

	public static Function<Object, Optional<String>> byApplicationLocalName(Collection<String> packages) {

		return toEventType().andThen(type -> packages.stream()
				.filter(it -> type.getPackageName().startsWith(it))
				.map(it -> type.getName().substring(it.length() + 1))
				.findFirst());
	}

	/**
	 * Returns a {@link Function} that looks up the target from the supported externalization annotations. The currently
	 * supported annotations are:
	 * <ul>
	 * <li>Spring Modulith's {@link Externalized}</li>
	 * <li>jMolecules {@link org.jmolecules.event.annotation.Externalized} (if present on the classpath)</li>
	 * </ul>
	 *
	 * @return will never be {@literal null}.
	 */
	public static Function<Object, Optional<String>> byExternalizedAnnotations() {
		return event -> AnnotationTargetLookup.of(event.getClass()).get();
	}

	/**
	 * Returns a {@link Function} that looks up the target from the fully-qualified type name of the event's type.
	 *
	 * @return will never be {@literal null}.
	 */
	public static Function<Object, String> byFullyQualifiedTypeName() {
		return toEventType().andThen(Class::getName);
	}

	private static Function<Object, Class<?>> toEventType() {
		return event -> event.getClass();
	}

	public static class Selector {

		private final @Nullable Predicate<Object> predicate;

		Selector() {
			this.predicate = DEFAULT_FILTER;
		}

		/**
		 * Selects events to externalize by applying the given {@link Predicate}.
		 *
		 * @param predicate will never be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router select(Predicate<Object> predicate) {
			return new Router(predicate);
		}

		/**
		 * Selects events to externalize by the given base package and all sub-packages.
		 *
		 * @param basePackage must not be {@literal null} or empty.
		 * @return will never be {@literal null}.
		 */
		public Router selectByPackage(String basePackage) {

			Assert.hasText(basePackage, "Base package must not be null or empty!");

			return select(it -> it.getClass().getPackageName().startsWith(basePackage));
		}

		/**
		 * Selects events to externalize by the package of the given type and all sub-packages.
		 *
		 * @param type must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByPackage(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return selectByPackage(type.getPackageName());
		}

		/**
		 * Selects events to externalize by the given base packages (and their sub-packages) that
		 *
		 * @param basePackages must not be {@literal null} or empty.
		 * @param filter must not be {@literal null}.
		 * @return
		 */
		public final Router selectByPackagesAndAnnotation(Collection<String> basePackages,
				Predicate<Object> filter) {

			Assert.notEmpty(basePackages, "Base packages must not be null or empty!");
			Assert.notNull(filter, "Filter must not be null!");

			BiPredicate<Object, String> matcher = (event, reference) -> event.getClass().getPackageName()
					.startsWith(reference);
			Predicate<Object> residesInPackage = it -> basePackages.stream().anyMatch(inner -> matcher.test(it, inner));

			return select(residesInPackage.and(filter));
		}

		/**
		 * Selects events to be externalized by inspecting the event type for the given annotation.
		 *
		 * @param type the annotation type to find on the event type, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByAnnotation(Class<? extends Annotation> type) {

			Assert.notNull(type, "Annotation type must not be null!");

			return select(it -> AnnotatedElementUtils.hasAnnotation(it.getClass(), type));
		}

		/**
		 * Selects events to be externalized by type.
		 *
		 * @param type the type that events to be externalized need to implement, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByType(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return select(type::isInstance);
		}

		/**
		 * Selects events to be externalized by the given {@link Predicate}.
		 *
		 * @param predicate must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByType(Predicate<Class<?>> predicate) {

			Assert.notNull(predicate, "Predicate must not be null!");

			return select(it -> predicate.test(it.getClass()));
		}

		public <T extends Annotation> EventExternalizationConfiguration selectAndRoute(Class<T> annotationType,
				Function<T, String> router) {

			Function<Object, T> extractor = it -> findAnnotation(it, annotationType);

			return selectByAnnotation(annotationType).route(it -> extractor.andThen(router).apply(it));
		}

		public <T extends Annotation> EventExternalizationConfiguration selectAndRoute(Class<T> annotationType,
				BiFunction<Object, T, String> router) {

			return selectByAnnotation(annotationType)
					.route(it -> router.apply(it, findAnnotation(it, annotationType)));
		}

		private static <T extends Annotation> T findAnnotation(Object event, Class<T> annotationType) {
			return findMergedAnnotation(event.getClass(), annotationType);
		}
	}

	public static class Router {

		private final Predicate<Object> filter;
		private final Function<Object, Object> mapper;
		private final @Nullable Function<Object, String> router;

		Router(Predicate<Object> filter, Function<Object, Object> mapper, Function<Object, String> router) {

			this.filter = filter;
			this.mapper = mapper;
			this.router = router;
		}

		Router(Predicate<Object> filter) {
			this(filter, Function.identity(), DEFAULT_ROUTER);
		}

		public Router mapping(Function<Object, Object> mapper) {
			return new Router(filter, mapper, router);
		}

		public <T> Router mapping(Class<T> type, Function<T, Object> mapper) {

			Function<Object, Object> combined = it -> {
				return type.isInstance(it)
						? mapper.apply(type.cast(it))
						: it;
			};

			return new Router(filter, this.mapper.compose(combined), router);
		}

		public Router routeMapped() {
			return new Router(filter, mapper, router.compose(mapper));
		}

		public DefaultEventExternalizationConfiguration route(Function<Object, String> router) {
			return new Router(filter, mapper, router).build();
		}

		/**
		 * Routes by extracting an {@link Optional} route from the event. If {@link Optional#empty()} is returned by the
		 * function, we will fall back to the currently configured routing.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public DefaultEventExternalizationConfiguration routeOptional(Function<Object, Optional<String>> router) {

			Assert.notNull(router, "Router must not be null!");

			Function<Object, String> foo = it -> router.apply(it).orElseGet(() -> this.router.apply(it));

			return new Router(filter, mapper, foo).build();
		}

		/**
		 * Routes by extracting an {@link Optional} route from the event type. If {@link Optional#empty()} is returned by
		 * the function, we will fall back to the currently configured routing.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public DefaultEventExternalizationConfiguration routeOptionalByType(
				Function<Class<?>, Optional<String>> router) {
			return routeOptional(it -> router.apply(it.getClass()));
		}

		public Router routeByType(Function<Class<?>, String> router) {
			return new Router(filter, mapper, it -> router.apply(it.getClass()));
		}

		public DefaultEventExternalizationConfiguration routeByTypeName() {
			return new Router(filter, mapper, DEFAULT_ROUTER).build();
		}

		public DefaultEventExternalizationConfiguration build() {
			return new DefaultEventExternalizationConfiguration(filter, mapper, router);
		}
	}
}
