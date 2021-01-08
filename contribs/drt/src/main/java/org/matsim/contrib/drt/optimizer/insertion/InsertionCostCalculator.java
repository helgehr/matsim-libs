/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package org.matsim.contrib.drt.optimizer.insertion;

import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.drt.optimizer.VehicleData;
import org.matsim.contrib.drt.optimizer.Waypoint;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.passenger.DrtRequestCreator;
import org.matsim.contrib.drt.routing.DefaultDrtRouteUpdater;
import org.matsim.contrib.drt.routing.DrtRouteCreator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtModeModule;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.core.mobsim.framework.MobsimTimer;

/**
 * @author michalm
 */
public class InsertionCostCalculator<D> {
	public interface PenaltyCalculator {
		double calcPenalty(double maxWaitTimeViolation, double maxTravelTimeViolation);
	}

	public static class RejectSoftConstraintViolations implements PenaltyCalculator {
		@Override
		public double calcPenalty(double maxWaitTimeViolation, double maxTravelTimeViolation) {
			return maxWaitTimeViolation > 0 || maxTravelTimeViolation > 0 ? INFEASIBLE_SOLUTION_COST : 0;
		}
	}

	public static class DiscourageSoftConstraintViolations implements PenaltyCalculator {
		//XXX try to keep penalties reasonably high to prevent people waiting or travelling for hours
		//XXX however, at the same time prefer max-wait-time to max-travel-time violations
		private static final double MAX_WAIT_TIME_VIOLATION_PENALTY = 1;// 1 second of penalty per 1 second of late departure
		private static final double MAX_TRAVEL_TIME_VIOLATION_PENALTY = 10;// 10 seconds of penalty per 1 second of late arrival

		@Override
		public double calcPenalty(double maxWaitTimeViolation, double maxTravelTimeViolation) {
			return MAX_WAIT_TIME_VIOLATION_PENALTY * maxWaitTimeViolation
					+ MAX_TRAVEL_TIME_VIOLATION_PENALTY * maxTravelTimeViolation;
		}
	}

	public static final double INFEASIBLE_SOLUTION_COST = Double.MAX_VALUE / 2;

	private final double stopDuration;
	private final DoubleSupplier timeOfDay;
	private final PenaltyCalculator penaltyCalculator;
	private final ToDoubleFunction<D> detourTime;
	private final InsertionDetourTimeCalculator<D> insertionDetourTimeCalculator;

	private final static Logger LOG = Logger.getLogger(InsertionCostCalculator.class.getName());
	private final static double DETOUR_DELTA = 1.5;
	//    private final static double RELATIVE_DELIVERY_DELTA = 3;
//	private static DrtModeModule.DrtRouteCreatorProvider drtRouteCreatorProvider;
	private static double L = 10000;
	private static double ERR = 1e-4;

	public InsertionCostCalculator(DrtConfigGroup drtConfig, MobsimTimer timer, PenaltyCalculator penaltyCalculator,
								   ToDoubleFunction<D> detourTime,
								   @Nullable DetourTimeEstimator replacedDriveTimeEstimator) {
		this(drtConfig.getStopDuration(), timer::getTimeOfDay, penaltyCalculator, detourTime,
				replacedDriveTimeEstimator);
	}

	public InsertionCostCalculator(double stopDuration, DoubleSupplier timeOfDay, PenaltyCalculator penaltyCalculator,
								   ToDoubleFunction<D> detourTime,
								   @Nullable DetourTimeEstimator replacedDriveTimeEstimator) {
		this.stopDuration = stopDuration;
		this.timeOfDay = timeOfDay;
		this.penaltyCalculator = penaltyCalculator;
		this.detourTime = detourTime;

		insertionDetourTimeCalculator = new InsertionDetourTimeCalculator<>(stopDuration, detourTime,
				replacedDriveTimeEstimator);

		LOG.warn("Calculating periodic distances assuming a 10000x10000 dimensioned grid network");
	}

//	public static void setDrtRouteCreatorProvider(DrtModeModule.DrtRouteCreatorProvider drtRouteCreatorProvider) {
//		InsertionCostCalculator.drtRouteCreatorProvider = drtRouteCreatorProvider;
//	}

	/**
	 * As the main goal is to minimise bus operation time, this method calculates how much longer the bus will operate
	 * after insertion. By returning a value equal or higher than INFEASIBLE_SOLUTION_COST, the insertion is considered
	 * infeasible
	 * <p>
	 * The insertion is invalid if some maxTravel/Wait constraints for the already scheduled requests are not fulfilled
	 * or the vehicle's time window is violated (hard constraints). This is denoted by returning INFEASIBLE_SOLUTION_COST.
	 * <p>
	 * However, not fulfilling the maxTravel/Time constraints (soft constraints) is penalised using
	 * PenaltyCalculator. If the penalty is at least as high as INFEASIBLE_SOLUTION_COST, the soft
	 * constraint becomes effectively a hard one.
	 *
	 * @param drtRequest the request
	 * @param insertion  the insertion to be considered here, with PickupIdx and DropoffIdx the positions
	 * @return cost of insertion (values higher or equal to INFEASIBLE_SOLUTION_COST represent an infeasible insertion)
	 */
	public double calculate(DrtRequest drtRequest, InsertionWithDetourData<D> insertion) {
		//TODO precompute time slacks for each stop to filter out even more infeasible insertions ???????????

		// Test pickup/dropoff ellipse
		InsertionGenerator.InsertionPoint pickupPoint = insertion.getPickup();
		InsertionGenerator.InsertionPoint dropoffPoint = insertion.getDropoff();
		if (isEllipseConstraintViolated(pickupPoint.previousWaypoint.getLink().getCoord(),
				pickupPoint.newWaypoint.getLink().getCoord(), pickupPoint.nextWaypoint.getLink().getCoord()) ||
			isEllipseConstraintViolated(dropoffPoint.previousWaypoint.getLink().getCoord(),
					dropoffPoint.newWaypoint.getLink().getCoord(), dropoffPoint.nextWaypoint.getLink().getCoord())) {
			return INFEASIBLE_SOLUTION_COST;
		}
		double pickupDetourTimeLoss = insertionDetourTimeCalculator.calculatePickupDetourTimeLoss(insertion);
		double dropoffDetourTimeLoss = insertionDetourTimeCalculator.calculateDropoffDetourTimeLoss(insertion);
		// the pickupTimeLoss is needed for stops that suffer only that one, while the sum of both will be suffered by
		// the stops after the dropoff stop. kai, nov'18
		// The computation is complicated; presumably, it takes care of this.  kai, nov'18

		// this is what we want to minimise
		double totalTimeLoss = pickupDetourTimeLoss + dropoffDetourTimeLoss;
		if (!checkHardConstraints(insertion, pickupDetourTimeLoss, totalTimeLoss)) {
			return INFEASIBLE_SOLUTION_COST;
		}

		return totalTimeLoss + calcSoftConstraintPenalty(drtRequest, insertion, pickupDetourTimeLoss);
	}

	private boolean checkHardConstraints(InsertionWithDetourData<?> insertion, double pickupDetourTimeLoss,
										 double totalTimeLoss) {
		return checkTimeConstraintsForScheduledRequests(insertion.getInsertion(), pickupDetourTimeLoss, totalTimeLoss)
				&& checkTimeConstraintsForVehicle(insertion.getVehicleEntry(), totalTimeLoss, timeOfDay.getAsDouble());
	}

	static boolean checkTimeConstraintsForScheduledRequests(InsertionGenerator.Insertion insertion,
															double pickupDetourTimeLoss, double totalTimeLoss) {
		VehicleData.Entry vEntry = insertion.vehicleEntry;
		final int pickupIdx = insertion.pickup.index;
		final int dropoffIdx = insertion.dropoff.index;

		// each existing stop has 2 time constraints: latestArrivalTime and latestDepartureTime (see: Waypoint.Stop)
		// we are looking only at the time constraints of the scheduled requests (the new request is checked separately)

		// all stops after the new (potential) pickup but before the new dropoff are delayed by pickupDetourTimeLoss
		// check if this delay satisfies the time constraints at these stops
		for (int s = pickupIdx; s < dropoffIdx; s++) {
			Waypoint.Stop stop = vEntry.stops.get(s);
			if (stop.task.getBeginTime() + pickupDetourTimeLoss > stop.latestArrivalTime
					|| stop.task.getEndTime() + pickupDetourTimeLoss > stop.latestDepartureTime
					|| isEllipseConstraintViolated(insertion.pickup.newWaypoint.getLink().getCoord(), stop.getLink()
					.getCoord(), insertion.dropoff.newWaypoint.getLink().getCoord())) {
				return false;
			}
		}

		// all stops after the new (potential) dropoff are delayed by totalTimeLoss
		// check if this delay satisfies the time constraints at these stops
		for (int s = dropoffIdx; s < vEntry.stops.size(); s++) {
			Waypoint.Stop stop = vEntry.stops.get(s);
			if (stop.task.getBeginTime() + totalTimeLoss > stop.latestArrivalTime
					|| stop.task.getEndTime() + totalTimeLoss > stop.latestDepartureTime) {
				return false;
			}
		}

		// calculating the direct TravelTime
//        RouteFactories routeFactories = new RouteFactories();
//        routeFactories.setRouteFactory(DrtRoute.class, new DrtRouteFactory());
//        DrtRoute directRoute = (DrtRoute) drtRouteCreatorProvider.get().createRoute(drtRequest.getSubmissionTime(),
//                drtRequest.getFromLink(),
//                drtRequest.getToLink(),
//                routeFactories);
//        double directTravelTime = directRoute.getDirectRideTime();
//        double detourTravelTime = IntStream.range(0, stopList.size() - 1)
//                .mapToObj(i -> (DrtRoute) drtRouteCreatorProvider.get().createRoute(drtRequest.getSubmissionTime(),
//                        stopList.get(i),
//                        stopList.get(i + 1),
//                        routeFactories)).mapToDouble(DrtRoute::getDirectRideTime).sum();
//        assert detourTravelTime > directTravelTime : "Travel Time of detour route must be higher than travel time of " +
//                "direct route!";
		//Alternative maybe (DefaultPassengerEngine):
//		Route route = ((Leg)((PlanAgent)passenger).getCurrentPlanElement()).getRoute();

		// Comparing direct and indirect traveltime for current request
//        LOG.warn("detourTravelTime: " + detourTravelTime +"\ndirectTravelTime: " + directTravelTime); // TODO:
//         direct times shorter than indirect ones??
//        if (detourTravelTime > RELATIVE_DELIVERY_DELTA*directTravelTime) {
//            LOG.warn("relative_delivery_delay_constraint violated");
//            return true;
//        }

		return true; //all time constraints of all stops are satisfied
	}

	static boolean checkTimeConstraintsForVehicle(VehicleData.Entry vEntry, double totalTimeLoss, double now) {
		// check if the vehicle operations end time is satisfied
		DrtStayTask lastTask = (DrtStayTask) Schedules.getLastTask(vEntry.vehicle.getSchedule());
		double timeSlack = vEntry.vehicle.getServiceEndTime() - Math.max(lastTask.getBeginTime(), now);
		return timeSlack >= totalTimeLoss;
	}

	public static class SoftConstraintViolation {
		public final double maxWaitTimeViolation;
		public final double maxTravelTimeViolation;

		public SoftConstraintViolation(double maxWaitTimeViolation, double maxTravelTimeViolation) {
			this.maxWaitTimeViolation = maxWaitTimeViolation;
			this.maxTravelTimeViolation = maxTravelTimeViolation;
		}
	}

	/**
	 * The request constraints are set in {@link DrtRequest}, which is used by {@link DrtRequestCreator},
	 * which is used by {@link DrtRouteCreator} and {@link DefaultDrtRouteUpdater}.  kai, nov'18
	 */
	private double calcSoftConstraintPenalty(DrtRequest drtRequest, InsertionWithDetourData<D> insertion,
											 double pickupDetourTimeLoss) {
		VehicleData.Entry vEntry = insertion.getVehicleEntry();
		final int pickupIdx = insertion.getPickup().index;
		final int dropoffIdx = insertion.getDropoff().index;

		double driveToPickupStartTime = vEntry.getWaypoint(pickupIdx).getDepartureTime();
		// (normally the end time of the previous task)
		double pickupEndTime = driveToPickupStartTime
				+ detourTime.applyAsDouble(insertion.getDetourToPickup())
				+ stopDuration;
		double dropoffStartTime = pickupIdx == dropoffIdx ?
				pickupEndTime + detourTime.applyAsDouble(insertion.getDetourFromPickup()) :
				// (special case if inserted dropoff is directly after inserted pickup)
				vEntry.stops.get(dropoffIdx - 1).task.getEndTime() + pickupDetourTimeLoss + detourTime.applyAsDouble(
						insertion.getDetourToDropoff());

		double maxWaitTimeViolation = Math.max(0, pickupEndTime - drtRequest.getLatestStartTime());
		// how much we are beyond the latest start time = request time + max wait time.
		// max wait time currently comes from config

		double maxTravelTimeViolation = Math.max(0, dropoffStartTime - drtRequest.getLatestArrivalTime());
		// how much we are beyond the latest dropoff time = request time + max travel time.
		// max travel time currently calculated with DrtRouteModule.getMaxTravelTime(unsharedRideTime)

		return penaltyCalculator.calcPenalty(maxWaitTimeViolation, maxTravelTimeViolation);
	}

	private static boolean isEllipseConstraintViolated(Coord previousCoord, Coord insertedCoord, Coord nextCoord) {
		double directDistance = calculateEuclideanDistancePeriodic(previousCoord, nextCoord);
//		double directDistance = calculateManhattanDistancePeriodic(previousCoord, nextCoord);
		double detourDistance = calculateEuclideanDistancePeriodic(previousCoord, insertedCoord)
				+ calculateEuclideanDistancePeriodic(insertedCoord, nextCoord);
//		double detourDistance = calculateManhattanDistancePeriodic(previousCoord, insertedCoord)
//				+ calculateManhattanDistancePeriodic(insertedCoord, nextCoord);
		boolean result = detourDistance > DETOUR_DELTA * directDistance;
		assert (detourDistance >= 0 && directDistance >= 0 && directDistance - detourDistance <= ERR) :
				"detour distance smaller than direct distance";

		return result;
	}

	private static double calculateEuclideanDistancePeriodic(Coord from, Coord to) {
		double deltaX = Math.abs(to.getX() - from.getX());
		double deltaXPeriodic = deltaX < L / 2 ? deltaX : -deltaX + L;
		double deltaY = Math.abs(to.getY() - from.getY());
		double deltaYPeriodic = deltaY < L / 2 ? deltaY : -deltaY + L;

		return Math.sqrt(deltaXPeriodic * deltaXPeriodic + deltaYPeriodic * deltaYPeriodic);
	}

	private static double calculateManhattanDistancePeriodic(Coord from, Coord to) {
		double deltaX = Math.abs(to.getX() - from.getX());
		double deltaXPeriodic = deltaX < L / 2 ? deltaX : -deltaX + L;
		double deltaY = Math.abs(to.getY() - from.getY());
		double deltaYPeriodic = deltaY < L / 2 ? deltaY : -deltaY + L;

		return deltaXPeriodic + deltaYPeriodic;
	}
}
