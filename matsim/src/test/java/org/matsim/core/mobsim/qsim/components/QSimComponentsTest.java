package org.matsim.core.mobsim.qsim.components;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.mobsim.qsim.components.mock.MockComponentAnnotation;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;
import org.matsim.core.scenario.ScenarioUtils;

public class QSimComponentsTest {
	@Test
	public void testExplicitAnnotationConfiguration() {
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		EventsManager eventsManager = EventsUtils.createEventsManager();

		MockEngine mockEngine = new MockEngine();

		new QSimBuilder(config) //
				.addQSimModule(new AbstractQSimModule() {
					@Override
					protected void configureQSim() {
						bindMobsimEngine(MockComponentAnnotation.class).toInstance(mockEngine);
					}
				}) //
				.configureComponents(components -> {
					components.addComponent(MockComponentAnnotation.class);
				}) //
				.build(scenario, eventsManager) //
				.run();

		Assert.assertTrue(mockEngine.isCalled);
	}
	
	@Test
	public void testManualConfiguration() {
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		EventsManager eventsManager = EventsUtils.createEventsManager();

		MockEngine mockEngine = new MockEngine();

		new QSimBuilder(config) //
				.addQSimModule(new AbstractQSimModule() {
					@Override
					protected void configureQSim() {
						bindNamedMobsimEngine("MockEngine").toInstance(mockEngine);
					}
				}) //
				.configureComponents(components -> {
					components.addNamedComponent("MockEngine");
				}) //
				.build(scenario, eventsManager) //
				.run();

		Assert.assertTrue(mockEngine.isCalled);
	}

	@Test
	public void testUseConfigGroup() {
		Config config = ConfigUtils.createConfig();

		QSimComponentsConfigGroup componentsConfig = new QSimComponentsConfigGroup();
		config.addModule(componentsConfig);
		
		componentsConfig.setActiveComponents(Collections.singletonList("MockEngine"));

		Scenario scenario = ScenarioUtils.createScenario(config);
		EventsManager eventsManager = EventsUtils.createEventsManager();

		MockEngine mockEngine = new MockEngine();

		new QSimBuilder(config) //
				.useDefaults() //
				.addQSimModule(new AbstractQSimModule() {
					@Override
					protected void configureQSim() {
						bindNamedMobsimEngine("MockEngine").toInstance(mockEngine);
					}
				}) //
				.build(scenario, eventsManager) //
				.run();

		Assert.assertTrue(mockEngine.isCalled);
	}

	private static class MockEngine implements MobsimEngine {
		public boolean isCalled = false;

		@Override
		public void doSimStep(double time) {
			isCalled = true;
		}

		@Override
		public void onPrepareSim() {

		}

		@Override
		public void afterSim() {

		}

		@Override
		public void setInternalInterface(InternalInterface internalInterface) {

		}
	}
}
