/* *********************************************************************** *
 * project: org.matsim.*
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

package d4d;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationReader;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.misc.NetworkUtils;
import org.matsim.core.utils.misc.Time;



/**
 * @author jbischoff
 * @author michaz
 *
 */

public class AltPopulationReaderMatsimV5 implements PopulationReader {

	private Scenario scenario;
	private Population population;
	
	public AltPopulationReaderMatsimV5(final Scenario scenario) {
		this.scenario = scenario;
		this.population = scenario.getPopulation();
	}

	private XMLInputFactory createXmlInputFactory(){
		XMLInputFactory xmlif = null;
		try {
			xmlif = XMLInputFactory.newInstance();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return xmlif;
	}

	@Override
	public void readFile(String filename) {
		File file = new File(filename);
		XMLInputFactory xmlif = this.createXmlInputFactory();
		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			XMLStreamReader xmlr = xmlif.createXMLStreamReader(filename, fileInputStream);
			while (xmlr.hasNext()) {
				int eventType = xmlr.next();
				if (eventType == XMLEvent.START_ELEMENT) {
					String name = xmlr.getLocalName();
					if ("population".compareTo(name) == 0){
						parsePopulation(xmlr);
					}
				}
			}
		} catch (XMLStreamException ex) {
			throw new RuntimeException(ex);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} 
	}

	private void parsePopulation(XMLStreamReader xmlr) throws XMLStreamException {
		String id = xmlr.getAttributeValue(""	, "name");
		this.population.setName(id);
		while (xmlr.hasNext()) {
			int eventType = xmlr.next();
			if (eventType == XMLEvent.START_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("person".compareTo(name ) == 0) {
					Person person = parsePerson(xmlr);
					this.population.addPerson(person);
				} 
			}
		}
	}

	private Person parsePerson(XMLStreamReader xmlr) throws XMLStreamException {
		String id = xmlr.getAttributeValue(""	, "id");
		PersonImpl person = new PersonImpl(new IdImpl(id));
		String sex = xmlr.getAttributeValue(""	, "sex");
		if (sex!=null) person.setSex(sex);
		String age = xmlr.getAttributeValue(""	, "age");
		if (age!=null) person.setAge(Integer.parseInt(age)); 
		String license = xmlr.getAttributeValue(""	, "license");
		if (license!=null) person.setLicence(license);
		String car = xmlr.getAttributeValue(""	, "car_avail");
		if (car!=null) person.setCarAvail(car);
		String employed = xmlr.getAttributeValue(""	, "employed");
		if (employed!=null) {
			if (employed.equals("yes")) {
				person.setEmployed(true);
			} else if (employed.equals("no")) {
				person.setEmployed(false);
			}
		}
		while (xmlr.hasNext()) {
			int eventType = xmlr.next();
			if (eventType == XMLEvent.START_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("travelcard".compareTo(name ) == 0) {
					String tc = xmlr.getAttributeValue("", "type");
					if (tc!=null) {
						person.addTravelcard(tc);
					}
				} else if ("plan".compareTo(name ) == 0) {
					Plan plan = parsePlan(xmlr);
					person.addPlan(plan);
				} 
			} else if (eventType == XMLEvent.END_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("person".compareTo(name) == 0) {
					return person;
				}
			}
		}
		throw new RuntimeException("Parse error.");
	}

	private Plan parsePlan(XMLStreamReader xmlr) throws XMLStreamException {
		Plan plan = new PlanImpl();
		String score = xmlr.getAttributeValue("", "score");
		if (score!=null) {
			plan.setScore(Double.parseDouble(score));
		}

		String type = xmlr.getAttributeValue("", "type");
		if (type!=null) {
			((PlanImpl) plan).setType(type);
		}
		while (xmlr.hasNext()) {
			int eventType = xmlr.next();
			if (eventType == XMLEvent.START_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("act".compareTo(name) == 0) {
					Activity activity = parseAct(xmlr);
					plan.addActivity(activity);
				} else if ("leg".compareTo(name) == 0) { 
					Leg leg = parseLeg(xmlr);
					plan.addLeg(leg);
				} 
			} else if (eventType == XMLEvent.END_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("plan".compareTo(name) == 0) {
					return plan;
				}
			}
		}
		throw new RuntimeException("Parse error.");
	}

	private Activity parseAct(XMLStreamReader xmlr) {
		ActivityImpl curract;
		Coord coord;
		if (xmlr.getAttributeValue("", "link") != null) {
			Id linkId = this.scenario.createId(xmlr.getAttributeValue("", "link"));
			curract = (ActivityImpl) population.getFactory().createActivityFromLinkId(xmlr.getAttributeValue("", "type"), linkId);
			if ((xmlr.getAttributeValue("", "x") != null) && (xmlr.getAttributeValue("", "y") != null)) {
				coord = new CoordImpl(xmlr.getAttributeValue("", "x"), xmlr.getAttributeValue("", "y"));
				curract.setCoord(coord);
			}
		} else if ((xmlr.getAttributeValue("", "x") != null) && (xmlr.getAttributeValue("", "y") != null)) {
			coord = new CoordImpl(xmlr.getAttributeValue(""	, "x"), xmlr.getAttributeValue("", "y"));
			curract = (ActivityImpl) population.getFactory().createActivityFromCoord(xmlr.getAttributeValue("", "type"), coord);
		} else {
			throw new IllegalArgumentException("In this version of MATSim either the coords or the link must be specified for an Act.");
		}
		curract.setStartTime(Time.parseTime(xmlr.getAttributeValue("", "start_time")));
		curract.setMaximumDuration(Time.parseTime(xmlr.getAttributeValue("", "dur")));
		curract.setEndTime(Time.parseTime(xmlr.getAttributeValue("", "end_time")));
		String fId = xmlr.getAttributeValue("", "facility");
		if (fId != null) {
			curract.setFacilityId(this.scenario.createId(fId));
		}
		return curract;
	}

	private Leg parseLeg(XMLStreamReader xmlr) throws XMLStreamException {
		String mode = xmlr.getAttributeValue(""	, "mode").toLowerCase(Locale.ROOT);
		if (mode.equals("undef")) {
			mode = "undefined";
		}
		Leg leg = population.getFactory().createLeg(mode.intern());
		leg.setDepartureTime(Time.parseTime(xmlr.getAttributeValue("", "dep_time")));
		leg.setTravelTime(Time.parseTime(xmlr.getAttributeValue("", "trav_time")));
		((LegImpl) leg).setArrivalTime(Time.parseTime(xmlr.getAttributeValue("", "arr_time")));
		while (xmlr.hasNext()) {
			int eventType = xmlr.next();
			if (eventType == XMLEvent.START_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("route".compareTo(name) == 0) { 
					Route route = parseRoute(xmlr);
					leg.setRoute(route);
				}
			} else if (eventType == XMLEvent.END_ELEMENT) {
				String name = xmlr.getLocalName();
				if ("leg".compareTo(name) == 0) {
					return leg;
				}
			}
		}
		throw new RuntimeException("Parse error.");
	}

	private Route parseRoute(XMLStreamReader xmlr) throws XMLStreamException {
		String routeType = xmlr.getAttributeValue("", "type");
		String dist = xmlr.getAttributeValue(""	, "dist");
		String travelTime = xmlr.getAttributeValue(""	, "trav_time");
		String routeDescription = xmlr.getElementText();
		Route currRoute;
		if ("links".equals(routeType)) {
			LinkNetworkRouteImpl linkNetworkRoute = new LinkNetworkRouteImpl(null, null);
			List<Id> linkIds = NetworkUtils.getLinkIds(routeDescription, this.scenario);
			Id startLinkId = linkIds.get(0);
			Id endLinkId = linkIds.get(linkIds.size()-1);
			if (linkIds.size() > 0) {
				linkIds.remove(0);
			}
			if (linkIds.size() > 0) {
				linkIds.remove(linkIds.size() - 1);
			}
			linkNetworkRoute.setLinkIds(startLinkId, linkIds, endLinkId);
			currRoute = linkNetworkRoute;
		} else if ("generic".equals(routeType)) {
			GenericRouteImpl genericRoute = new GenericRouteImpl(null, null);
			genericRoute.setRouteDescription(null, routeDescription.trim(), null);
			currRoute = genericRoute;
		} else {
			throw new RuntimeException("unknown route type: " + routeType);
		}
		if (dist != null) {
			currRoute.setDistance(Double.parseDouble(dist));
		}
		if (travelTime != null) {
			currRoute.setTravelTime(Time.parseTime(travelTime));
		}
		return currRoute;
	}

}
