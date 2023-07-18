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
package org.springframework.modulith.events.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalizationConfiguration.RoutingTarget;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.modulith.events.core.EventExternalizer;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
abstract class AbstractEventExternalizer implements EventExternalizer, ConditionalEventListener {

	private static final Logger logger = LoggerFactory.getLogger(AbstractEventExternalizer.class.getClass());

	private final EventExternalizationConfiguration configuration;

	/**
	 * Creates a new {@link AbstractEventExternalizer} for the given {@link EventExternalizationConfiguration}.
	 *
	 * @param configuration must not be {@literal null}.
	 */
	protected AbstractEventExternalizer(EventExternalizationConfiguration configuration) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");

		this.configuration = configuration;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.ConditionalEventListener#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(Object event) {
		return configuration.supports(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.foo.EventExternalizer#externalize(java.lang.Object)
	 */
	@Override
	@ApplicationModuleListener
	public void externalize(Object event) {

		if (!configuration.supports(event)) {
			return;
		}

		var target = configuration.determineTarget(event);
		var mapped = configuration.map(event);

		if (logger.isTraceEnabled()) {
			logger.trace("Externalizing event of type {} to {}, payload: {}).", event.getClass(), target, mapped);
		} else if (logger.isDebugEnabled()) {
			logger.debug("Externalizing event of type {} to {}.", event.getClass(), target);
		}

		externalize(target, mapped);
	}

	protected abstract void externalize(RoutingTarget target, Object payload);
}
