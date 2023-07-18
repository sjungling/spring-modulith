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

import static org.assertj.core.api.Assertions.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.DefaultEventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalizationConfiguration.RoutingTarget;

/**
 * Unit tests for {@link DefaultEventExternalizationConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class DefaultEventExternalizationConfigurationUnitTests {

	@Test // GH-248
	void filtersEventByAnnotation() {

		var filter = EventExternalizationConfiguration.externalizing()
				.selectByAnnotation(Externalized.class)
				.build();

		var event = new SampleEvent();

		assertThat(filter.supports(event)).isTrue();
		assertThat(filter.supports(new Object())).isFalse();
		assertThat(filter.determineTarget(event))
				.isEqualTo(new RoutingTarget(SampleEvent.class.getName()));
	}

	@Test // GH-248
	void routesByAnnotationAttribute() {

		var filter = EventExternalizationConfiguration.externalizing()
				.selectAndRoute(Externalized.class, Externalized::value);

		var event = new SampleEvent();

		assertThat(filter.supports(event)).isTrue();
		assertThat(filter.determineTarget(event)).isEqualTo(new RoutingTarget("target"));
	}

	@Test // GH-248
	void mapsSourceEventBeforeSerializing() {

		var configuration = EventExternalizationConfiguration.externalizing()
				.select(__ -> true)
				.mapping(SampleEvent.class, it -> "foo")
				.mapping(AnotherSampleEvent.class, it -> "bar")
				.build();

		assertThat(configuration.map(new SampleEvent())).isEqualTo("foo");
		assertThat(configuration.map(new AnotherSampleEvent())).isEqualTo("bar");
		assertThat(configuration.map(4711L)).isEqualTo(4711L);
	}

	@Test // GH-248
	void setsUpMappedRouting() {

		var configuration = EventExternalizationConfiguration.externalizing()
				.select(__ -> true)
				.mapping(SampleEvent.class, it -> "foo")
				.routeMapped()
				.build();

		assertThat(configuration.determineTarget(new SampleEvent()))
				.isEqualTo(new RoutingTarget(String.class.getName()));
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface Externalized {
		String value() default "";
	}

	@Externalized("target")
	static class SampleEvent {}

	static class AnotherSampleEvent {}
}
