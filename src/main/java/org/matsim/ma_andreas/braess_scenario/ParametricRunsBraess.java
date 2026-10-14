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
package org.matsim.ma_andreas.braess_scenario;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.QSimConfigGroup.NodeTransition;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.ma_andreas.braess_scenario.analysis.AnalyzeBraess;
import org.matsim.ma_andreas.braess_scenario.analysis.general.*;
import org.matsim.vehicles.VehicleType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Random;

/**
 * @author tthunig
 */
public class ParametricRunsBraess {
	
	private static final SCALE_TYPE vehScaling = SCALE_TYPE.DIRECTLY; // alpha
	private static final SCALE_TYPE timeScaling = SCALE_TYPE.DIRECTLY; // beta
	private enum SCALE_TYPE {
		NONE,
		DIRECTLY,
		INDIRECTLY,
	};
			
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		String overallAnalysisDir = "matsimOutput/braess/";
		String overallAnalysisDir = "../../runs-svn/braess/refinement/";
		overallAnalysisDir += OutputUtils.getCurrentDateIncludingTime() + "_vehScaling" + vehScaling + "_timeScaling" + timeScaling + "/";
		new File(overallAnalysisDir).mkdirs();
		final PrintStream overallRunTimeWritingStream = prepareOverallStream(overallAnalysisDir + "runTime.txt", "beta \talpha \trandomSeed \trunTime");

//		for (int scaleBetaInverse=1; scaleBetaInverse<=64 ; scaleBetaInverse*=2) {
		for (int scaleBetaInverse=1; scaleBetaInverse<=64 ; scaleBetaInverse*=2) {
			final double simulationPeriod = 100; // this should stay constant
//			double totalNumberOfAgents = 100;
			double flowCapFactor = 1.0;
			double storageCapFactor = flowCapFactor;
			double pcuEquivalents = 1.0;
			double vehLength = 7.5;
			double simTimeStepSize = 1.0;
			double travelTimeFactor = 1.0;
			double inflowRate = 1.0; // in volume/ vehicle mass
			
			// alpha=beta^2
			int scaleAlphaInverse = scaleBetaInverse * scaleBetaInverse;
			// alpha=beta
//			double scaleAlpha = scaleBeta;
			
			switch (vehScaling) { // alpha
			case DIRECTLY:
				// scale the size of the vehicles down
				pcuEquivalents /= scaleAlphaInverse;
				vehLength /= scaleAlphaInverse;
//				totalNumberOfAgents /= scaleAlpha;
				break;
			case INDIRECTLY:
				// scale capacities up 
//				totalNumberOfAgents /= scaleAlpha;
				flowCapFactor *= scaleAlphaInverse;
				storageCapFactor *= scaleAlphaInverse;
				inflowRate *= scaleAlphaInverse;
				break;
			}
			switch (timeScaling) { // beta
			case DIRECTLY:
				// scale the simulation time step size down
				simTimeStepSize /= scaleBetaInverse;
				break;
			case INDIRECTLY:
				// use time step size 1s but scale up travel times, storage cap, and number of vehicles -> 1sec is less relevant
				travelTimeFactor *= scaleBetaInverse;
				storageCapFactor *= scaleBetaInverse;
				inflowRate /= scaleBetaInverse; // TODO is this correct? This also effect totalNumberOfAgents? why is totalNumberOfAgents *= scaleBeta missing?
				break;
			}
			
			double agentsPerSec = inflowRate / pcuEquivalents;
			
			String thisScaleAnalysisDir = overallAnalysisDir + "beta" + scaleBetaInverse + "/"; //+ "_alpha" + scaleAlphaInverse
			new File(thisScaleAnalysisDir).mkdir();

//			final PrintStream overallTraveltimeWritingStream = prepareOverallStream(thisScaleAnalysisDir + "avgRouteTTsPerDepartureMultipleRuns.txt", "DepartureTime \tavg tt 0");

			Random random = new Random(4711);

			int firstRandom = 1;
			for (int randomSeedNumber = 1; randomSeedNumber < firstRandom; randomSeedNumber++){
				random.nextInt(10000);
			}

			for (int randomSeedNumber=firstRandom; randomSeedNumber <=1; randomSeedNumber++) {
				// run each configuration multiple times with different random seeds
				int randomSeed = random.nextInt(10000);

				Time.setDefaultTimeFormat(Time.TIMEFORMAT_HHMMSSDOTSS);

				Config config = RunBraessSimulation.defineConfig(500);
				
				config.controller().setRunId("beta"+scaleBetaInverse+"random"+randomSeedNumber);
				
				config.plans().setInputFile(null);

//				config.controller().setOutputDirectory(thisScaleAnalysisDir);
				config.controller().setOutputDirectory(thisScaleAnalysisDir + "random" + randomSeedNumber + "/");
				config.global().setRandomSeed(randomSeed); // the MATSim default is 4711
			
				config.qsim().setFlowCapFactor(flowCapFactor);
				config.qsim().setStorageCapFactor(storageCapFactor);
				config.qsim().setTimeStepSize(simTimeStepSize);
//				config.qsim().setUsingFastCapacityUpdate(false);
				
//				config.qsim().setTrafficDynamics(TrafficDynamics.kinematicWaves);
//				config.qsim().setTrafficDynamics(TrafficDynamics.withHoles);
//				config.qsim().setSnapshotStyle(SnapshotStyle.withHoles);
				
				// TODO adjust here
//				config.qsim().setNodeTransitionLogic(NodeTransition.moveVehByVehDeterministicPriorities_nodeBlockedWhenSingleOutlinkFull);
//				config.qsim().setNodeTransitionLogic(NodeTransition.moveVehByVehRandomDistribution_dontBlockNode);
				config.qsim().setNodeTransitionLogic(NodeTransition.emptyBufferAfterBufferRandomDistribution_dontBlockNode);

				// TODO think about dependency between brain exp beta and time step size
//				config.planCalcScore().setBrainExpBeta(scaleBetaInverse);
				config.scoring().setBrainExpBeta(10);

				// use the same time bin size in the travel time calculator (for routing)
				config.travelTimeCalculator().setTraveltimeBinSize(simTimeStepSize);

				// --- create the scenario
				Scenario scenario = ScenarioUtils.loadScenario(config);
				RunBraessSimulation.createNetwork(scenario);
				RunBraessSimulation.createPopulation(scenario, agentsPerSec, simulationPeriod);
				
				// create a vehicle type with correct pcu equivalents and length 
				VehicleType myVehType = scenario.getVehicles().getFactory().createVehicleType(Id.create("defaultVehicleType", VehicleType.class));
				myVehType.setPcuEquivalents(pcuEquivalents);
				myVehType.setLength(vehLength);
				scenario.getVehicles().addVehicleType(myVehType);
				// create a vehicle of this type for all agents
				for (Id<Person> personId : scenario.getPopulation().getPersons().keySet()) {
					scenario.getVehicles().addVehicle(scenario.getVehicles().getFactory().createVehicle(Id.createVehicleId(personId), myVehType));					
				}
				
				// go through all links and scale travel times
				for (Link link : scenario.getNetwork().getLinks().values()) {
					link.setFreespeed(link.getFreespeed() / travelTimeFactor);
				}
				
				// --- create the controler
				Controler c = new Controler(scenario);

				c.addOverridingModule(new AbstractModule() {
					@Override
					public void install() {
						// TODO
//						this.addControlerListenerBinding().to(ChangeStrategyWeightOverIterations.class).in(Singleton.class);
						
						this.bind(AbstractAnalysisTool.class).to(AnalyzeBraess.class).asEagerSingleton();
						this.addControlerListenerBinding().to(ListenerToBindAndWriteAnalysis.class);
						this.addEventHandlerBinding().to(AbstractAnalysisTool.class);
						this.bind(AnalyzedResultsWriter.class);
						
						// TODO make sure travel times are written to the overallTraveltimeWritingStream
//						AnalyzeSingleCorridorTravelTimes myGeneralAnalysisTool = new AnalyzeSingleCorridorTravelTimes();
//						this.bind(TtAbstractAnalysisTool.class).toInstance(myGeneralAnalysisTool);
//						this.addControlerListenerBinding().to(ListenerToBindAndWriteSingleCorridorAnalysis.class);
//						this.addEventHandlerBinding().to(TtAbstractAnalysisTool.class);
//						TtAnalyzedResultsWriter myGeneralResultsWriter = new TtAnalyzedResultsWriter(scenario, myGeneralAnalysisTool);
//						myGeneralResultsWriter.setTravelTimeWritingStreamForParametricRuns(overallTraveltimeWritingStream);
//						this.bind(TtAnalyzedResultsWriter.class).toInstance(myGeneralResultsWriter);
					}
				});

				// enable decongestion pricing
//				c.addOverridingModule(new DecongestionModule(scenario));
//				// toll-adjusted routing
//				final TollTimeDistanceTravelDisutilityFactory travelDisutilityFactory = new TollTimeDistanceTravelDisutilityFactory();
//				c.addOverridingModule(new AbstractModule(){
//					@Override
//					public void install() {
//						this.bindCarTravelDisutilityFactory().toInstance( travelDisutilityFactory );
//					}
//				});
					
				// measure run time
				long runTime = System.currentTimeMillis();
				// --- run the simulation
				c.run();
				// write run time in stream dependent on scaleBeta
				// note, that other analysis gets written during the simulation, i.e. is included in the run time
				runTime = System.currentTimeMillis() - runTime;
				overallRunTimeWritingStream.println(scaleBetaInverse + " \t" + scaleAlphaInverse + " \t" + randomSeed + " \t" + runTime);
			}
			
//			overallTraveltimeWritingStream.close();
			
			// plot overall analysis	
//			String relativePathToGnuplotScript = "../../../../scenarios/singleCorridor/analysis/plot_boxPlotsForMultipleRuns_traveltimesAndThroughput.p";	
//			log.info("execute command: cd " + thisScaleAnalysisDir);
//			log.info("and afterwards: gnuplot " + relativePathToGnuplotScript);
//			GnuplotUtils.runGnuplotScript(thisScaleAnalysisDir, relativePathToGnuplotScript);
		}
		
		overallRunTimeWritingStream.close();
		// plot run time vs. beta scale
		String relativePathToGnuplotScript = "../../../scenarios/braess/analysis/plot_runTimePerScale.p";
		System.out.println("ParametricRunsBraess: execute command: cd " + overallAnalysisDir);
		System.out.println("ParametricRunsBraess: and afterwards: gnuplot " + relativePathToGnuplotScript);
		GnuplotUtils.runGnuplotScript(overallAnalysisDir, relativePathToGnuplotScript);
	}

	private static PrintStream prepareOverallStream(String pathToFile, String header) {
		try {
			PrintStream stream = new PrintStream(new File(pathToFile));
			stream.println(header);
			return stream;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

}
