package org.matsim.contrib.carsharing.manager.routers;

import java.util.List;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
/** 
 * 
 * @author balac
 */
public interface RouteCarsharingTrip {	

	public List<PlanElement> routeCarsharingTrip(Plan plan, Leg legToBeRouted, double time);

}
