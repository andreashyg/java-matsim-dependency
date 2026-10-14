/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */
package org.matsim.ma_andreas.braess_scenario.analysis;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

/**
 * @author tthunig
 */
public class RunPostSimulationBrassAnalysis {

	private static String runDirectory = "matsimOutput/braess/2023-06-30-9-22-13_vehScalingDIRECTLY_timeScalingDIRECTLY_500s_1000it_only16_rand20/";
	private static final int LAST_IT = 1000;

	public static void main(String[] args) {

		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(runDirectory + "output_network.xml.gz");

		for (int scaleBetaInverse=16; scaleBetaInverse<=16 ; scaleBetaInverse*=2) {
			for (int randomSeedNumber = 1; randomSeedNumber <= 20; randomSeedNumber++) {

				String runPathToLastItDir = runDirectory + "beta" + scaleBetaInverse + "/random" + randomSeedNumber +
						"/ITERS/it." + LAST_IT + "/";

				// analyzer writes results per time step directly while analyzing into the given writing streams
//				BraessLinkQueueLengthAnalyzer queueAnalyzer =
//						new BraessLinkQueueLengthAnalyzer(runPathToLastItDir, scaleBetaInverse);
//
//				BraessDelayedVehiclesAnalyzer delayedVehiclesAnalyzer =
//						new BraessDelayedVehiclesAnalyzer(network, runPathToLastItDir, scaleBetaInverse);

				BraessLinkTTAnalyzer linkTTAnalyzer =
						new BraessLinkTTAnalyzer(network, runPathToLastItDir, scaleBetaInverse);

				EventsManager events = EventsUtils.createEventsManager();
//				events.addHandler(queueAnalyzer);
//				events.addHandler(delayedVehiclesAnalyzer);
				events.addHandler(linkTTAnalyzer);
				new MatsimEventsReader(events).readFile(runPathToLastItDir + "beta" + scaleBetaInverse
						+ "random" + randomSeedNumber + "." + LAST_IT + ".events.xml.gz");

//				queueAnalyzer.closeStream();
//				delayedVehiclesAnalyzer.writeAnalysis();
				linkTTAnalyzer.writeAnalysis();
			}
		}

	}



}
