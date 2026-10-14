/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * DefaultControlerModules.java
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.LanesWriter;
import org.matsim.ma_andreas.braess_scenario.TtCreateBraessNetworkAndLanes.LaneType;
import org.matsim.ma_andreas.braess_scenario.TtCreateBraessPopulation.InitRoutes;
import org.matsim.ma_andreas.braess_scenario.analysis.AnalyzeBraess;
import org.matsim.ma_andreas.braess_scenario.analysis.general.AbstractAnalysisTool;
import org.matsim.ma_andreas.braess_scenario.analysis.general.AnalyzedResultsWriter;
import org.matsim.ma_andreas.braess_scenario.analysis.general.ListenerToBindAndWriteAnalysis;
import org.matsim.ma_andreas.braess_scenario.analysis.general.OutputUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * Class to run a simulation of the braess scenario with or without signals. 
 * It analyzes the simulation with help of TtAnalyzeBraess.java.
 * 
 * @author tthunig
 * 
 */
public final class RunBraessSimulation {

	private static final Logger log = LogManager.getLogger(RunBraessSimulation.class);

	/* population parameter */
	
	private static final double NUMBER_OF_PERSONS_PER_SEC = 1; // per second
	private static final double SIMULATION_PERIOD_S = 100; // in seconds
	private static final double SIMULATION_START_TIME = 0.0; // seconds from midnight
	
	private static final InitRoutes INIT_ROUTES_TYPE = InitRoutes.ALL;
	// initial score for all initial plans (if to low, to many agents switch to outer routes simultaneously)
	private static final Double INIT_PLAN_SCORE = null; // should be definitely higher than the executed ones at the beginning such that they will be chosen and updated
	
	// defines which kind of lanes should be used
	private static final LaneType LANE_TYPE = LaneType.NONE;
	
	// defines which kind of pricing should be used
	private static final PricingType PRICING_TYPE = PricingType.NONE;
	public enum PricingType{
		NONE, V3, V4, V7, V8, V9, V10, INTERVALBASED
	}
	
	private static final boolean VIS = false;

	// choose a sigma for the randomized router
	// (higher sigma cause more randomness. use 0.0 for no randomness.)
	private static final double SIGMA = 0.0;	
		
	private static final boolean WRITE_INITIAL_FILES = true;
	
	private static final String OUTPUT_BASE_DIR = "../../runs-svn/braess/refinement/";

	private static final String networkFileName = "network.xml";
	private static final String lanesFileName = "lanes.xml";
	private static final String plansFileName = "plans.xml";
	
	public static void main(String[] args) {
		Config config = defineConfig(200);
		Scenario scenario = prepareScenario(config);
		Controler controler = prepareController(scenario);

//		controler.getConfig().vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);
		controler.run();
	}

	static Config defineConfig(int lastIteration) {
		Config config = ConfigUtils.createConfig();

		// set number of iterations
		config.controller().setLastIteration(lastIteration);

		// able or enable signals and lanes
		config.qsim().setUseLanes(LANE_TYPE.equals(LaneType.NONE) ? false : true);
//		config.qsim().setUsingFastCapacityUpdate(false);
		
		// set brain exp beta
		config.scoring().setBrainExpBeta(2);

		// choose between link to link and node to node routing
		// (only has effect if lanes are used)
		boolean link2linkRouting = false;
		config.controller().setLinkToLinkRoutingEnabled(link2linkRouting);

		config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(link2linkRouting);
		config.travelTimeCalculator().setCalculateLinkTravelTimes(true);

		// set travelTimeBinSize (only has effect if reRoute is used)
//		config.travelTimeCalculator().setTraveltimeBinSize(60);
		config.travelTimeCalculator().setTraveltimeBinSize(1);
//		config.travelTimeCalculator().setMaxTime((int) (3600 * (SIMULATION_START_TIME + SIMULATION_PERIOD + 2)));
		config.travelTimeCalculator().setMaxTime(3600 * 24);
		
		config.timeAllocationMutator().setMutationRange(60);

		// TODO adapt here
		// define strategies:
		config.replanning().setFractionOfIterationsToDisableInnovation(.8);
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultStrategy.ReRoute.toString());
			strat.setWeight(0.1);
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultStrategy.TimeAllocationMutator.toString());
			strat.setWeight(0.0);
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultSelector.SelectRandom.toString());
			strat.setWeight(0.0);
//			strat.setDisableAfter((int) (config.controller().getLastIteration() * 0.5));
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultSelector.ChangeExpBeta.toString());
			strat.setWeight(0.0);
//			strat.setDisableAfter((int) (config.controller().getLastIteration() * 0.8));
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultSelector.SelectExpBeta.toString());
			strat.setWeight(0.9);
//			strat.setDisableAfter(config.controller().getLastIteration());
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultSelector.BestScore.toString());
			strat.setWeight(0.0);
//			strat.setDisableAfter(config.controller().getLastIteration() - 50);
			config.replanning().addStrategySettings(strat);
		}
		{
			ReplanningConfigGroup.StrategySettings strat = new ReplanningConfigGroup.StrategySettings();
			strat.setStrategyName(DefaultSelector.KeepLastSelected.toString());
			strat.setWeight(0.0);
//			strat.setDisableAfter(config.controller().getLastIteration());
			config.replanning().addStrategySettings(strat);
		}

		// TODO adapt here
		config.scoring().setFractionOfIterationsToStartScoreMSA(0.8);
		config.scoring().setLearningRate(1.); // nur relevant für bereich vor MSA!

		config.replanning().setMaxAgentPlanMemorySize(5);

		config.qsim().setNodeTransitionLogic(QSimConfigGroup.NodeTransition.emptyBufferAfterBufferRandomDistribution_dontBlockNode); // default
//		config.qsim().setNodeTransitionLogic(QSimConfigGroup.NodeTransition.moveVehByVehRandomDistribution_dontBlockNode);
//		config.qsim().setNodeTransitionLogic(QSimConfigGroup.NodeTransition.moveVehByVehDeterministicPriorities_nodeBlockedWhenSingleOutlinkFull);

		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.queue); // default
//		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);

		config.qsim().setStuckTime(3600 * 24.);
		config.qsim().setRemoveStuckVehicles(false);

		config.qsim().setStartTime(SIMULATION_START_TIME);
		// set end time to shorten simulation run time: 5 hours after the last agent departs
		config.qsim().setEndTime(SIMULATION_START_TIME + SIMULATION_PERIOD_S + 3600 * 5);
//		config.qsim().setEndTime(3600 * 24);
		
		// adapt monetary distance cost rate (should be negative)
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0);
		// TODO adapt marginal utility of traveling (for car); default: -6.0
//		config.planCalcScore().getModes().get(TransportMode.car).setMarginalUtilityOfTraveling(-60);

		config.scoring().setMarginalUtilityOfMoney(1.0); // default is 1.0

		// "overwriteExistingFiles" necessary if initial files should be written out
		config.controller().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		// note: the output directory is defined in createRunNameAndOutputDir(...) after all adaptations are done

		config.vspExperimental().setWritingOutputEvents(true);
		config.scoring().setWriteExperiencedPlans(true);

//		int lastIt = config.controller().getLastIteration();
//		config.controller().setWriteEventsInterval(lastIt<50? (lastIt==0? 1 : lastIt) : 50);
//		config.controller().setWritePlansInterval(lastIt<50? (lastIt==0? 1 : lastIt) : 50);

		ScoringConfigGroup.ActivityParams dummyAct = new ScoringConfigGroup.ActivityParams("dummy");
		dummyAct.setTypicalDuration(12 * 3600);
		config.scoring().addActivityParams(dummyAct);

		config.controller().setCreateGraphs(true);

		// decongestion relevant parameters
//		DecongestionConfigGroup decongestionSettings = new DecongestionConfigGroup();
//		decongestionSettings.setWriteOutputIteration(100);
//		decongestionSettings.setUpdatePriceInterval(10);
//		decongestionSettings.setTollBlendFactor(1.0);
//		decongestionSettings.setFractionOfIterationsToStartPriceAdjustment(0.05);
//		decongestionSettings.setFractionOfIterationsToEndPriceAdjustment(1.0);
//		decongestionSettings.setToleratedAverageDelaySec(2);
//		decongestionSettings.setWriteLinkInfoCharts(true);
//		decongestionSettings.setMsa(true);
//
//		decongestionSettings.setDecongestionApproach(DecongestionConfigGroup.DecongestionApproach.PID);
//		decongestionSettings.setTollAdjustment(1);
//		decongestionSettings.setInitialToll(1);
//		decongestionSettings.setKp(0.1);
//		decongestionSettings.setKi(0.);
//		decongestionSettings.setKd(0.);
//
//		config.addModule(decongestionSettings);
		
		if (VIS) {
			throw new RuntimeException("not implemented here. include dependency in pom");
//			OTFVisConfigGroup otfvisConfig = ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.class);
//			otfvisConfig.setDrawTime(true);
//			otfvisConfig.setAgentSize(80f);
//			config.qsim().setSnapshotStyle(QSimConfigGroup.SnapshotStyle.withHoles);
		}

		// set params according to vsp config consistency checker
		config.global().setInsistingOnDeprecatedConfigVersion(false);
		config.plans().setRemovingUnneccessaryPlanAttributes(true);

		return config;
	}

	static Scenario prepareScenario(Config config) {
		Scenario scenario = ScenarioUtils.loadScenario(config);
		createNetwork(scenario);
		createPopulation(scenario);
		createRunNameAndOutputDir(scenario);
	
		if (WRITE_INITIAL_FILES) 
			writeInitFiles(scenario);
		
		return scenario;
	}

	private static Controler prepareController(Scenario scenario) {
		Config config = scenario.getConfig();
		config.routing().setRoutingRandomness(SIGMA);
		Controler controler = new Controler(scenario);

		if (!PRICING_TYPE.equals(PricingType.NONE) && !PRICING_TYPE.equals(PricingType.INTERVALBASED)){
			throw new RuntimeException("vsp playground including toll handlers is not included in the dependencies here... fix that if you want to use it");
//			// add tolling
//			TollHandler tollHandler = new TollHandler(scenario);
//			
//			// add correct TravelDisutilityFactory for tolls if ReRoute is used
//			StrategySettings[] strategies = config.strategy().getStrategySettings()
//					.toArray(new StrategySettings[0]);
//			for (int i = 0; i < strategies.length; i++) {
//				if (strategies[i].getStrategyName().equals(DefaultStrategy.ReRoute.toString())){
//					if (strategies[i].getWeight() > 0.0){ // ReRoute is used
//						final CongestionTollTimeDistanceTravelDisutilityFactory factory =
//								new CongestionTollTimeDistanceTravelDisutilityFactory(
//										new RandomizingTimeDistanceTravelDisutilityFactory( TransportMode.car, config.planCalcScore() ),
//								tollHandler, config.planCalcScore()
//							) ;
//						factory.setSigma(SIGMA);
//						controler.addOverridingModule(new AbstractModule(){
//							@Override
//							public void install() {
//								this.bindCarTravelDisutilityFactory().toInstance( factory );
//							}
//						});
//					}
//				}
//			}		
//			
//			// choose the correct congestion handler and add it
//			EventHandler congestionHandler = null;
//			switch (PRICING_TYPE){
//			case V3:
//				congestionHandler = new CongestionHandlerImplV3(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			case V4:
//				congestionHandler = new CongestionHandlerImplV4(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			case V7:
//				congestionHandler = new CongestionHandlerImplV7(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			case V8:
//				congestionHandler = new CongestionHandlerImplV8(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			case V9:
//				congestionHandler = new CongestionHandlerImplV9(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			case V10:
//				congestionHandler = new CongestionHandlerImplV10(controler.getEvents(), 
//						controler.getScenario());
//				break;
//			default:
//				break;
//			}
//			controler.addControlerListener(
//					new MarginalCongestionPricingContolerListener(scenario, tollHandler, congestionHandler));
//		
		} else if (PRICING_TYPE.equals(PricingType.INTERVALBASED)) {
			
			throw new RuntimeException("not implemented here. add decongestion dependency in pom");
			
//			controler.addOverridingModule(new DecongestionModule(scenario));
//
//			// toll-adjusted routing
//
//			final TollTimeDistanceTravelDisutilityFactory travelDisutilityFactory = new TollTimeDistanceTravelDisutilityFactory();
//
//			controler.addOverridingModule(new AbstractModule(){
//				@Override
//				public void install() {
//					this.bindCarTravelDisutilityFactory().toInstance( travelDisutilityFactory );
//				}
//			});
						
		} else { // no pricing
			
			// adapt sigma for randomized routing
			final RandomizingTimeDistanceTravelDisutilityFactory builder =
					new RandomizingTimeDistanceTravelDisutilityFactory( TransportMode.car, config );
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					bindCarTravelDisutilityFactory().toInstance(builder);
				}
			});
		}
		
		if (VIS) {
			throw new RuntimeException("not implemented here. add otfvis dependency in pom");
//			controler.addOverridingModule(new OTFVisLiveModule());
		}
		
		controler.addOverridingModule(new AbstractModule() {			
			@Override
			public void install() {
				// bind Christians ForceInnovationStrategyChooser that ensures that agents are not selected for
				// innovation too seldomly. note: each agent is selected each n-th iteration for sure, i.e. higher
				// n-values mean less innovation. Permute.no means that agents are assigned into fixed groups that
				// are then always selected together each n-th iteration
				// TODO adapt here if needed
//				binder().bind(new TypeLiteral<StrategyChooser<Plan, Person>>() {}).toInstance(
//						new ForceInnovationStrategyChooser<>(100, ForceInnovationStrategyChooser.Permute.no));

				// bind analysis
				this.bind(AbstractAnalysisTool.class).to(AnalyzeBraess.class).asEagerSingleton();
				this.addControlerListenerBinding().to(ListenerToBindAndWriteAnalysis.class);
				this.addEventHandlerBinding().to(AbstractAnalysisTool.class);
				this.bind(AnalyzedResultsWriter.class);
			}
		});
		
		return controler;
	}

	static void createNetwork(Scenario scenario) {	
		
		TtCreateBraessNetworkAndLanes netCreator = new TtCreateBraessNetworkAndLanes(scenario);
//		netCreator.setUseBTUProperties( false );
		netCreator.setSimulateInflowCap( false );
		netCreator.setMiddleLinkExists( true );
		
		netCreator.setCapFirstLast(3600);
		netCreator.setCapZ(1./3*3600);
		netCreator.setCapFast(1./2*3600);
		netCreator.setCapSlow(1*3600);
		
		netCreator.setLaneType(LANE_TYPE);
		netCreator.setNumberOfPersonsPerSec(NUMBER_OF_PERSONS_PER_SEC);
		
		netCreator.createNetworkAndLanes();
	}

	static void createPopulation(Scenario scenario) {
		
		TtCreateBraessPopulation popCreator = 
				new TtCreateBraessPopulation(scenario.getPopulation(), scenario.getNetwork());
		popCreator.setNumberOfPersonsPerSec(NUMBER_OF_PERSONS_PER_SEC);
		popCreator.setSimulationPeriod(SIMULATION_PERIOD_S);
		popCreator.setSimulationStartTime(SIMULATION_START_TIME);
		
		popCreator.createPersons(INIT_ROUTES_TYPE, INIT_PLAN_SCORE);
	}
	
	// used for parametric runs
	static void createPopulation(Scenario scenario, double numberOfAgentsPerSec, double simulationPeriod) {
		
		TtCreateBraessPopulation popCreator = 
				new TtCreateBraessPopulation(scenario.getPopulation(), scenario.getNetwork());
		popCreator.setNumberOfPersonsPerSec(numberOfAgentsPerSec);
		popCreator.setSimulationPeriod(simulationPeriod);
		popCreator.setSimulationStartTime(0);
		
		popCreator.createPersons(INIT_ROUTES_TYPE, INIT_PLAN_SCORE);
	}

	private static void createRunNameAndOutputDir(Scenario scenario) {

		String outputDir = OUTPUT_BASE_DIR + OutputUtils.getCurrentDateIncludingTime() + "/";
		// create directory
		new File(outputDir).mkdirs();

		scenario.getConfig().controller().setOutputDirectory(outputDir);
		log.info("The output will be written to " + outputDir);
		
		writeRunDescription(outputDir, createRunName(scenario));
	}
	
	private static String createRunName(Scenario scenario) {
		Config config = scenario.getConfig();
		
		String runName = NUMBER_OF_PERSONS_PER_SEC + "p/s";
		runName += "_" + SIMULATION_PERIOD_S + "s";
		runName += "_start" + (int)SIMULATION_START_TIME; 
		
		switch(INIT_ROUTES_TYPE){
		case ALL:
			runName += "_ALL"; //"_ALL-sel1+3";
			break;
		case ONLY_OUTER:
			runName += "_OUTER";
			break;
		case ONLY_MIDDLE:
			runName += "_MIDDLE";
			break;
		default: // e.g. NONE
			break;
		}
		if (INIT_PLAN_SCORE != null)
			runName += "-score" + INIT_PLAN_SCORE;

		runName += "_" + config.controller().getLastIteration() + "it";

		// create info about the different possible travel times, capacities and link length
		Link middleLink = scenario.getNetwork().getLinks()
				.get(Id.createLinkId("3_4"));
		Link slowLink = scenario.getNetwork().getLinks()
				.get(Id.createLinkId("3_5"));
		Link fastLink = scenario.getNetwork().getLinks().containsKey(Id.createLinkId("2_3"))? 
				scenario.getNetwork().getLinks().get(Id.createLinkId("2_3")) : 
					scenario.getNetwork().getLinks().get(Id.createLinkId("23_3"));
		// note: link 3_5 always exists. link 2_3 only exists if inflow links are not used. it is 23_3 otherwise.
		if (middleLink == null){
			runName += "_woZ";
		} else {
			int middleTT = (int)Math.ceil(middleLink.getLength()
					/ middleLink.getFreespeed());
			int fastTT = (int)Math.ceil(fastLink.getLength()
					/ fastLink.getFreespeed());
			int slowTT = (int)Math.ceil(slowLink.getLength()
					/ slowLink.getFreespeed());
			int capZ = (int)middleLink.getCapacity();
			int capFast = (int)fastLink.getCapacity();
			int capSlow = (int)slowLink.getCapacity();
			if (fastTT != 60 || middleTT != 60 || slowTT != 600){
				runName += "_tt-" + fastTT + "-" + middleTT + "-" + slowTT;
			}
			runName += "_cap-" + capFast + "-" + capZ + "-" + capSlow;
			if (fastLink.getLength() != 1000){
				runName += "_l-" + (int)fastLink.getLength() + "-" + (int)middleLink.getLength() + "-" + (int)slowLink.getLength();
			}
		}
		
		if (scenario.getNetwork().getNodes().containsKey(Id.createNodeId(23))){
			runName += "_inflow";
			
			Link inflowLink = scenario.getNetwork().getLinks()
					.get(Id.createLinkId("2_23"));
			if (inflowLink.getLength() != 7.5)
				runName += inflowLink.getLength();
		}
		
		ReplanningConfigGroup.StrategySettings[] strategies = config.replanning().getStrategySettings()
				.toArray(new ReplanningConfigGroup.StrategySettings[0]);
		for (int i = 0; i < strategies.length; i++) {
			double weight = strategies[i].getWeight();
			if (weight != 0.0){
				String name = strategies[i].getStrategyName();
				if (name.equals(DefaultSelector.ChangeExpBeta)){
					runName += "_ChExp" + weight;
					runName += "_beta" + (int)config.scoring().getBrainExpBeta();
				} else if (name.equals(DefaultSelector.KeepLastSelected)){
					runName += "_KeepLast" + weight;
				} else if (name.equals(DefaultStrategy.ReRoute)){
					runName += "_ReRoute" + weight;
					runName += "_tbs" + config.travelTimeCalculator().getTraveltimeBinSize();
				} else if (name.equals(DefaultStrategy.TimeAllocationMutator)){
					runName += "_TimeAll" + weight;
					runName += "_range" + config.timeAllocationMutator().getMutationRange();
				} else {
					runName += "_" + name + weight;
				}
			}
		}
		
		if (SIGMA != 0.0)
			runName += "_sigma" + SIGMA;
		if (config.scoring().getModes().get(TransportMode.car).getMonetaryDistanceRate() != 0.0)
			runName += "_distCost"
					+ config.scoring().getModes().get(TransportMode.car).getMonetaryDistanceRate();

		if (LANE_TYPE.equals(LaneType.TRIVIAL)) {
			runName += "_trivialLanes";
		}
		else if (LANE_TYPE.equals(LaneType.REALISTIC)){
			runName += "_lanes";
		}

		// link 2 link vs node 2 node routing. this only has an effect if lanes are used
		if (LANE_TYPE.equals(LaneType.REALISTIC)){
			if (config.controller().isLinkToLinkRoutingEnabled())
				runName += "_link";
			else
				runName += "_node";
		}			
		
		if (!PRICING_TYPE.equals(PricingType.NONE)){
			if (PRICING_TYPE.equals(PricingType.INTERVALBASED)){
				runName += "_INTERVAL_tbs" + config.travelTimeCalculator().getTraveltimeBinSize();
			} else {
				runName += "_" + PRICING_TYPE.toString();
			}
		}
		
		if (config.replanning().getMaxAgentPlanMemorySize() != 0)
			runName += "_" + config.replanning().getMaxAgentPlanMemorySize() + "pl";
		
		runName += "_stuckT" + (int)config.qsim().getStuckTime();
		if (!config.qsim().getEndTime().isUndefined())
			runName += "_simEndT" + (int)(config.qsim().getEndTime().seconds()/24) + "h";
		
		return runName;
	}

	private static void writeRunDescription(String outputDir, String runName){
		PrintStream stream;
		String filename = outputDir + "runDescription.txt";
		try {
			stream = new PrintStream(new File(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		stream.println(runName);
		stream.close();
	}

	private static void writeInitFiles(Scenario scenario) {
		String outputDir = scenario.getConfig().controller().getOutputDirectory() + "initialFiles/";
		// create directory
		new File(outputDir).mkdirs();
		
		// write network and lanes
		new NetworkWriter(scenario.getNetwork()).write(outputDir + networkFileName);
		if (!LANE_TYPE.equals(LaneType.NONE)) {
			new LanesWriter(scenario.getLanes()).write(outputDir + lanesFileName);
		}

		// write population
		new PopulationWriter(scenario.getPopulation()).write(outputDir + plansFileName);

		// write config
		new ConfigWriter(scenario.getConfig()).write(outputDir + "config.xml");

		// read in the config again to be able to change the input files according to the here written
		// network, lane and plans files. overwrite the other config...
		Config readConfigAgainToChangeInputFileNames = ConfigUtils.loadConfig(outputDir + "config.xml");
		readConfigAgainToChangeInputFileNames.network().setInputFile(networkFileName);
		readConfigAgainToChangeInputFileNames.network().setLaneDefinitionsFile(lanesFileName);
		readConfigAgainToChangeInputFileNames.plans().setInputFile(plansFileName);
		new ConfigWriter(readConfigAgainToChangeInputFileNames).write(outputDir + "config.xml");
	}
}
