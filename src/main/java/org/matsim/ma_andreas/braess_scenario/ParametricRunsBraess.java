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
import picocli.CommandLine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Random;

/**
 * @author tthunig
 */
public class ParametricRunsBraess {

	private static final String RUN_NAME = "original_settings_but_only_leg_scoring_and_brain_1";

	private static final String OVERALL_ANALYSIS_DIR = "../../../runs-svn/braess/refinement/no_spillback_scenario/";

	private static final int MIN_RANDOM_SEED_NUMBER = 1;
	private static final int MAX_RANDOM_SEED_NUMBER = 20;
	private static final int RANDOM_SEED_STREAM_SEED = 4711;
	private static final int RANDOM_SEED_UPPER_BOUND = 10000;

	private static final int MIN_BETA = 1;
	private static final int MAX_BETA = 64;


	@CommandLine.Command(
			name = "ParametricRunsBraess",
			mixinStandardHelpOptions = true
	)
	private static class CliArgs {
		@CommandLine.Option(
				names = "--randomSeedNumber",
				paramLabel = "N",
				description = "Run only this random seed number (1..20)."
		)
		private Integer optionRandomSeedNumber;

		@CommandLine.Option(
				names = "--beta",
				paramLabel = "B",
				description = "Run only this beta scale (1, 2, 4, 8, 16, 32, 64)."
		)
		private Integer optionBeta;

		private Integer getRandomSeedNumber() {
			return optionRandomSeedNumber;
		}
		private Integer getOptionBeta() {
			return optionBeta;
		}
	}
	
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
		Integer singleRandomSeedNumber = parseRandomSeedNumberArgument(args);
		Integer singleBeta = parseBetaArgument(args);

//		String overallAnalysisDir = "matsimOutput/braess/";
		String overallAnalysisDir = OVERALL_ANALYSIS_DIR;
//		overallAnalysisDir += OutputUtils.getCurrentDateIncludingTime() + "_vehScaling" + vehScaling + "_timeScaling" + timeScaling;
//		overallAnalysisDir += OutputUtils.getCurrentDate() + "_vehScaling" + vehScaling + "_timeScaling" + timeScaling;
		overallAnalysisDir += OutputUtils.getCurrentDate() + "_" + RUN_NAME;

//		// if we run for a single random seed number, we must create a subdirectory, because otherwise we get an
//		// inconsistent mixture of output directories, depending on when GNU parallel decided to start the runs.
//		if (singleRandomSeedNumber != null) {
//			overallAnalysisDir += "_random" + singleRandomSeedNumber;
//		}
//		// same thing for a single beta number
//		if (singleBeta != null) {
//			overallAnalysisDir += "_beta" + singleBeta;
//		}
		overallAnalysisDir += "/";

		new File(overallAnalysisDir).mkdirs();
		String runtimeFilePath = overallAnalysisDir + "runTime";
		if (singleRandomSeedNumber != null) {
			runtimeFilePath += "_random" + singleRandomSeedNumber;
		}
		if (singleBeta != null) {
			runtimeFilePath += "_beta" + singleBeta;
		}
		runtimeFilePath += ".txt";

		final PrintStream overallRunTimeWritingStream = prepareOverallStream(runtimeFilePath, "beta \talpha \trandomSeed \trunTime");

//		for (int scaleBetaInverse=1; scaleBetaInverse<=64 ; scaleBetaInverse*=2) {
		int startBeta = singleBeta == null ? MIN_BETA : singleBeta;
		int endBeta = singleBeta == null ? MAX_BETA : singleBeta;
		for (int scaleBetaInverse=startBeta; scaleBetaInverse<=endBeta ; scaleBetaInverse*=2) {
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

			Random random = new Random(RANDOM_SEED_STREAM_SEED);

			int firstRandom = 1;
			int startRandomSeedNumber = singleRandomSeedNumber == null ? firstRandom : singleRandomSeedNumber;
			int endRandomSeedNumber = singleRandomSeedNumber == null ? MAX_RANDOM_SEED_NUMBER : singleRandomSeedNumber;

			if (startRandomSeedNumber < firstRandom) {
				throw new IllegalArgumentException("Requested randomSeedNumber " + startRandomSeedNumber
						+ " is smaller than firstRandom " + firstRandom + ".");
			}

			for (int randomSeedNumber = MIN_RANDOM_SEED_NUMBER; randomSeedNumber < startRandomSeedNumber; randomSeedNumber++){
				random.nextInt(RANDOM_SEED_UPPER_BOUND);
			}

			for (int randomSeedNumber=startRandomSeedNumber; randomSeedNumber <= endRandomSeedNumber; randomSeedNumber++) {
				// run each configuration multiple times with different random seeds
				int randomSeed = random.nextInt(RANDOM_SEED_UPPER_BOUND);

				Time.setDefaultTimeFormat(Time.TIMEFORMAT_HHMMSSDOTSS);

				Config config = RunBraessSimulation.defineConfig(500);
//				Config config = RunBraessSimulation.defineConfig(0);

				config.controller().setRunId("beta"+scaleBetaInverse+"random"+randomSeedNumber);
				
				config.plans().setInputFile(null);

//				config.controller().setOutputDirectory(thisScaleAnalysisDir);
				config.controller().setOutputDirectory(thisScaleAnalysisDir + "random" + randomSeedNumber + "/");
				config.global().setRandomSeed(randomSeed); // the MATSim default is 4711

				config.global().setNumberOfThreads(1);  // use only one thread, and instead run this script in parallel

				config.qsim().setFlowCapFactor(flowCapFactor);
				config.qsim().setStorageCapFactor(storageCapFactor);
				config.qsim().setTimeStepSize(simTimeStepSize);
//				config.qsim().setUsingFastCapacityUpdate(false);
				
//				config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves); // TODO comment this for default value "queue"
//				config.qsim().setTrafficDynamics(TrafficDynamics.withHoles);
//				config.qsim().setSnapshotStyle(SnapshotStyle.withHoles);
				
				// TODO adjust here
//				config.qsim().setNodeTransitionLogic(NodeTransition.moveVehByVehDeterministicPriorities_nodeBlockedWhenSingleOutlinkFull);
//				config.qsim().setNodeTransitionLogic(NodeTransition.moveVehByVehRandomDistribution_dontBlockNode);
				config.qsim().setNodeTransitionLogic(NodeTransition.emptyBufferAfterBufferRandomDistribution_dontBlockNode);

				config.qsim().setNumberOfThreads(1);  // use only one thread, and instead run this script in parallel


				// TODO think about dependency between brain exp beta and time step size
//				config.planCalcScore().setBrainExpBeta(scaleBetaInverse);
				config.scoring().setBrainExpBeta(1);

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
				myVehType.setNetworkMode("car");
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
//		String relativePathToGnuplotScript = "../../../scenarios/braess/analysis/plot_runTimePerScale.p";
//		System.out.println("ParametricRunsBraess: execute command: cd " + overallAnalysisDir);
//		System.out.println("ParametricRunsBraess: and afterwards: gnuplot " + relativePathToGnuplotScript);
//		GnuplotUtils.runGnuplotScript(overallAnalysisDir, relativePathToGnuplotScript);
	}

	private static Integer parseRandomSeedNumberArgument(String[] args) {
		CliArgs cliArgs = new CliArgs();
		CommandLine commandLine = new CommandLine(cliArgs);
		CommandLine.ParseResult parseResult;
		try {
			parseResult = commandLine.parseArgs(args);
		} catch (CommandLine.ParameterException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (CommandLine.printHelpIfRequested(parseResult)) {
			System.exit(0);
		}

		Integer randomSeedNumber = cliArgs.getRandomSeedNumber();
		if (randomSeedNumber == null) {
			return null;
		}
		if (randomSeedNumber < MIN_RANDOM_SEED_NUMBER || randomSeedNumber > MAX_RANDOM_SEED_NUMBER) {
			throw new IllegalArgumentException("randomSeedNumber must be in [" + MIN_RANDOM_SEED_NUMBER + ", "
					+ MAX_RANDOM_SEED_NUMBER + "], but was " + randomSeedNumber + ".");
		}
		return randomSeedNumber;
	}

	private static Integer parseBetaArgument(String[] args) {
		CliArgs cliArgs = new CliArgs();
		CommandLine commandLine = new CommandLine(cliArgs);
		CommandLine.ParseResult parseResult;
		try {
			parseResult = commandLine.parseArgs(args);
		} catch (CommandLine.ParameterException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (CommandLine.printHelpIfRequested(parseResult)) {
			System.exit(0);
		}

		Integer beta = cliArgs.getOptionBeta();
		if (beta == null) {
			return null;
		}
		if (beta < MIN_BETA || beta > MAX_BETA) {
			throw new IllegalArgumentException("beta must be in [" + MIN_BETA + ", " + MAX_BETA + "], but was " + beta + ".");
		}
		return beta;
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
