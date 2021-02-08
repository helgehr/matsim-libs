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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator.Insertion;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.path.OneToManyPathSearch.PathData;
import org.matsim.contrib.zone.skims.DvrpTravelTimeMatrix;
import org.matsim.core.mobsim.framework.MobsimTimer;

/**
 * @author michalm
 */
public class SelectiveInsertionSearch implements DrtInsertionSearch<PathData> {

	// step 1: initial filtering out feasible insertions
	private final DetourTimeEstimator restrictiveDetourTimeEstimator;
	private final BestInsertionFinder<Double> initialInsertionFinder;

	// step 2: finding best insertion
	private final ForkJoinPool forkJoinPool;
	private final DetourPathCalculator detourPathCalculator;
	private final BestInsertionFinder<PathData> bestInsertionFinder;

	public SelectiveInsertionSearch(DetourPathCalculator detourPathCalculator, DrtConfigGroup drtCfg, MobsimTimer timer,
			ForkJoinPool forkJoinPool, CostCalculationStrategy costCalculationStrategy,
			DvrpTravelTimeMatrix dvrpTravelTimeMatrix) {
		this.detourPathCalculator = detourPathCalculator;
		this.forkJoinPool = forkJoinPool;

		double restrictiveBeelineSpeedFactor = ((SelectiveInsertionSearchParams)drtCfg.getDrtInsertionSearchParams()).getRestrictiveBeelineSpeedFactor();
		restrictiveDetourTimeEstimator = DetourTimeEstimator.createFreeSpeedZonalTimeEstimator(
				restrictiveBeelineSpeedFactor, dvrpTravelTimeMatrix);

		initialInsertionFinder = new BestInsertionFinder<>(
				new InsertionCostCalculator<>(drtCfg, timer, costCalculationStrategy, Double::doubleValue,
						restrictiveDetourTimeEstimator));

		bestInsertionFinder = new BestInsertionFinder<>(
				new InsertionCostCalculator<>(drtCfg, timer, costCalculationStrategy, PathData::getTravelTime, null));
	}

	@Override
	public Optional<InsertionWithDetourData<PathData>> findBestInsertion(DrtRequest drtRequest,
			Collection<VehicleEntry> vehicleEntries) {
		InsertionGenerator insertionGenerator = new InsertionGenerator();
		DetourData<Double> restrictiveTimeData = DetourData.create(restrictiveDetourTimeEstimator, drtRequest);

		// Parallel outer stream over vehicle entries. The inner stream (flatmap) is sequential.
		Optional<Insertion> bestInsertion = forkJoinPool.submit(
				// find best insertion given a stream of insertion with time data
				() -> initialInsertionFinder.findBestInsertion(drtRequest,
						//for each vehicle entry
						vehicleEntries.parallelStream()
								//generate feasible insertions (wrt occupancy limits)
								.flatMap(e -> insertionGenerator.generateInsertions(drtRequest, e).stream())
								//map them to insertions with admissible detour times
								.map(restrictiveTimeData::createInsertionWithDetourData))
						.map(InsertionWithDetourData::getInsertion)).join();

		if (bestInsertion.isEmpty()) {
			return Optional.empty();
		}

		//compute actual path
		DetourData<PathData> pathData = detourPathCalculator.calculatePaths(drtRequest, List.of(bestInsertion.get()));
		return bestInsertionFinder.findBestInsertion(drtRequest, bestInsertion.
				stream().map(pathData::createInsertionWithDetourData));
	}
}
