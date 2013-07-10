/* *********************************************************************** *
 * project: org.matsim.*
 * WaitTimeCalculator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package playground.singapore.transitRouterEventsBased;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.AgentStuckEvent;
import org.matsim.core.api.experimental.events.PersonEntersVehicleEvent;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentStuckEventHandler;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.population.routes.GenericRoute;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * Save waiting times of agents while mobsim is running
 * 
 * @author sergioo
 */

public class WaitTimeStuckCalculator implements AgentDepartureEventHandler, PersonEntersVehicleEventHandler, AgentStuckEventHandler {
	
	//Constants
	private final static String SEPARATOR = "===";

	//Attributes
	private final double timeSlot;
	private final Map<TransitRoute, Map<Id, WaitTimeData>> waitTimes = new HashMap<TransitRoute, Map<Id, WaitTimeData>>(1000);
	private final Map<TransitRoute, Map<Id, double[]>> scheduledWaitTimes = new HashMap<TransitRoute, Map<Id, double[]>>(1000);
	private final Map<Id, Double> agentsWaitingData = new HashMap<Id, Double>();
	private final Map<Id, Integer> agentsCurrentLeg = new HashMap<Id, Integer>();
	private final Population population;
	private TransitSchedule transitSchedule;

	//Constructors
	public WaitTimeStuckCalculator(final Population population, final TransitSchedule transitSchedule, final Config config) {
		this(population, transitSchedule, config.travelTimeCalculator().getTraveltimeBinSize(), (int) (config.getQSimConfigGroup().getEndTime()-config.getQSimConfigGroup().getStartTime()));
	}
	public WaitTimeStuckCalculator(final Population population, final TransitSchedule transitSchedule, final int timeSlot, final int totalTime) {
		this.population = population;
		this.transitSchedule = transitSchedule;
		this.timeSlot = timeSlot;
		for(TransitLine line:transitSchedule.getTransitLines().values())
			for(TransitRoute route:line.getRoutes().values()) {
				double[] sortedDepartures = new double[route.getDepartures().size()];
				int d=0;
				for(Departure departure:route.getDepartures().values())
					sortedDepartures[d++] = departure.getDepartureTime();
				Arrays.sort(sortedDepartures);
				Map<Id, WaitTimeData> stopsMap = new HashMap<Id, WaitTimeData>(100);
				Map<Id, double[]> stopsScheduledMap = new HashMap<Id, double[]>(100);
				for(TransitRouteStop stop:route.getStops()) {
					stopsMap.put(stop.getStopFacility().getId(), new WaitTimeDataArray((int) (totalTime/timeSlot)+1));
					double[] cacheWaitTimes = new double[(int) (totalTime/timeSlot)+1];
					for(int i=0; i<cacheWaitTimes.length; i++) {
						double endTime = timeSlot*(i+1);
						if(endTime>24*3600)
							endTime-=24*3600;
						cacheWaitTimes[i] = Time.UNDEFINED_TIME;
						SORTED_DEPARTURES:
						for(double departure:sortedDepartures) {
							double arrivalTime = departure+(stop.getArrivalOffset()!=Time.UNDEFINED_TIME?stop.getArrivalOffset():stop.getDepartureOffset()); 
							if(arrivalTime>=endTime) {
								cacheWaitTimes[i] = arrivalTime-endTime;
								break SORTED_DEPARTURES;
							}
						}
						if(cacheWaitTimes[i]==Time.UNDEFINED_TIME)
							cacheWaitTimes[i] = sortedDepartures[0]+24*3600+(stop.getArrivalOffset()!=Time.UNDEFINED_TIME?stop.getArrivalOffset():stop.getDepartureOffset())-endTime;
					}
					stopsScheduledMap.put(stop.getStopFacility().getId(), cacheWaitTimes);
				}
				waitTimes.put(route, stopsMap);
				scheduledWaitTimes.put(route, stopsScheduledMap);
			}
	}

	//Methods
	public WaitTime getWaitTimes() {
		return new WaitTime() {
			
			@Override
			public double getRouteStopWaitTime(TransitLine line, TransitRoute route, Id stopId, double time) {
				return WaitTimeStuckCalculator.this.getRouteStopWaitTime(line, route, stopId, time);
			}
		
		};
	}
	private double getRouteStopWaitTime(TransitLine line, TransitRoute route, Id stopId, double time) {
		WaitTimeData waitTimeData = waitTimes.get(route).get(stopId);
		double tTime = waitTimeData.getNumData((int) (time/timeSlot));
		if(tTime==0) {
			double[] waitTimes = scheduledWaitTimes.get(route).get(stopId);
			return waitTimes[(int) (time/timeSlot)<waitTimes.length?(int) (time/timeSlot):(waitTimes.length-1)];
		}
		else
			return tTime;
	}
	@Override
	public void reset(int iteration) {
		for(Map<Id, WaitTimeData> routeData:waitTimes.values())
			for(WaitTimeData waitTimeData:routeData.values())
				waitTimeData.resetWaitTimes();
		agentsWaitingData.clear();
		agentsCurrentLeg.clear();
	}
	@Override
	public void handleEvent(AgentDepartureEvent event) {
		Integer currentLeg = agentsCurrentLeg.get(event.getPersonId());
		if(currentLeg == null)
			currentLeg = 0;
		else
			currentLeg++;
		agentsCurrentLeg.put(event.getPersonId(), currentLeg);
		if(event.getLegMode().equals("pt") && agentsWaitingData.get(event.getPersonId())==null)
			agentsWaitingData.put(event.getPersonId(), event.getTime());
		else if(agentsWaitingData.get(event.getPersonId())!=null)
			new RuntimeException("Departing with old data");
	}
	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Double startWaitingTime = agentsWaitingData.get(event.getPersonId());
		if(startWaitingTime!=null) {
			int legs = 0, currentLeg = agentsCurrentLeg.get(event.getPersonId());
			PLAN_ELEMENTS:
			for(PlanElement planElement:population.getPersons().get(event.getPersonId()).getSelectedPlan().getPlanElements())
				if(planElement instanceof Leg) {
					if(currentLeg==legs) {
						String[] leg = ((GenericRoute)((Leg)planElement).getRoute()).getRouteDescription().split(SEPARATOR);
						WaitTimeData data = waitTimes.get(transitSchedule.getTransitLines().get(new IdImpl(leg[2])).getRoutes().get(new IdImpl(leg[3]))).get(new IdImpl(leg[1]));
						data.addWaitTime((int) (startWaitingTime/timeSlot), event.getTime()-startWaitingTime);
						agentsWaitingData.remove(event.getPersonId());
						break PLAN_ELEMENTS;
					}
					else
						legs++;
				}
		}
	}
	
	@Override
	public void handleEvent(AgentStuckEvent event) {
		Double startWaitingTime = agentsWaitingData.get(event.getPersonId());
		if(startWaitingTime!=null) {
			int legs = 0, currentLeg = agentsCurrentLeg.get(event.getPersonId());
			PLAN_ELEMENTS:
			for(PlanElement planElement:population.getPersons().get(event.getPersonId()).getSelectedPlan().getPlanElements())
				if(planElement instanceof Leg) {
					if(currentLeg==legs) {
						String[] leg = ((GenericRoute)((Leg)planElement).getRoute()).getRouteDescription().split(SEPARATOR);
						WaitTimeData data = waitTimes.get(transitSchedule.getTransitLines().get(new IdImpl(leg[2])).getRoutes().get(new IdImpl(leg[3]))).get(new IdImpl(leg[1]));
						if(data!=null)
							data.addWaitTime((int) (startWaitingTime/timeSlot), event.getTime()-startWaitingTime);
						agentsWaitingData.remove(event.getPersonId());
						break PLAN_ELEMENTS;
					}
					else
						legs++;
				}
		}
	}

}
