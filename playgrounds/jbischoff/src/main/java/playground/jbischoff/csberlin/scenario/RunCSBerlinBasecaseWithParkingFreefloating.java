/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */
package playground.jbischoff.csberlin.scenario;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;

import playground.jbischoff.analysis.TripHistogram;
import playground.jbischoff.analysis.TripHistogramListener;
import playground.jbischoff.ffcs.FFCSConfigGroup;
import playground.jbischoff.ffcs.sim.SetupFreefloatingParking;
import playground.jbischoff.parking.sim.SetupParking;

/**
 * @author  jbischoff
 *
 */
/**
 *
 */
public class RunCSBerlinBasecaseWithParkingFreefloating {
	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig("../../../shared-svn/projects/bmw_carsharing/data/scenario/configBCParkingFreeFloat11.xml", new FFCSConfigGroup());
		String runId = "bc11_2000ffc";
		config.controler().setOutputDirectory("D:/runs-svn/bmw_carsharing/basecase/"+runId);
		config.controler().setRunId(runId);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
	
		Controler controler = new Controler(config);
		SetupFreefloatingParking.installFreefloatingParkingModules(controler, (FFCSConfigGroup) config.getModule("freefloating"));
		
		controler.addOverridingModule(new AbstractModule() {
			
			@Override
			public void install() {
				addControlerListenerBinding().to(TripHistogramListener.class).asEagerSingleton();
				bind(TripHistogram.class).asEagerSingleton();
			}
		});
		
		controler.run();
		
		
	}
}