package org.matsim.ma_andreas;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import picocli.CommandLine;
import org.jspecify.annotations.NonNull;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.traffic.TravelTimeAnalysis;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.GenerateShortDistanceTrips;
import org.matsim.application.prepare.population.TrajectoryToPlans;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import static org.matsim.api.core.v01.Id.createVehicleTypeId;

@CommandLine.Command( header = ":: RunSingleBraessNoSpillbackIteration ::", version = "1.0")
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class
})
@MATSimApplication.Analysis({
		TravelTimeAnalysis.class
})
public class SingleBraessNoSpillbackIteration extends MATSimApplication {

	@CommandLine.Option(
			names = "--existingRunsDir",
			description = "Directory containing existing runs to read from."
	)
	private String existingRunsDir;

	@CommandLine.Option(
			names = "--baseOutputDir",
			description = "Base output directory for the simulation run."
	)
	private String baseOutputDir;

	@CommandLine.Option(
			names = "--replanningVariant",
			description = "Replanning variant to use for the simulation run."
	)
	private String replanningVariant;

	@CommandLine.Option(
			names = "--beta",
			description = "Beta parameter for the simulation run."
	)
	private Integer beta;

	@CommandLine.Option(
			names = "--readFromRandom",
			description = "Random seed of the run to read from. This is used to read the plans file from a previous run."
	)
	private Integer readFromRandom;

	@CommandLine.Option(
			names = "--useRandom",
			description = "Random seed to use for the current run."
	)
	private Integer useRandom;

	@CommandLine.Option(
			names = "--delete-output-dir-if-existing",  // no camelcase to be more consistent with the rust flag
			description = "Delete output directory if it already exists."
	)
	private boolean deleteOutputDirIfExisting;

	@CommandLine.Option(
			names = "--outputDir",
			description = "Output directory for the simulation run."
	)
	private String outputDir;

	public SingleBraessNoSpillbackIteration() {
		super();
	}

	public static void main(String[] args) {
		MATSimApplication.execute(SingleBraessNoSpillbackIteration.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		// get (longer) replanning variant string, used in the folder structure of the original java runs
		if (replanningVariant == null) {
			throw new IllegalArgumentException("Missing --replanningVariant");
		}
		// TODO technically, this should also be read from the config.yaml
		String replanning_string_for_original_dir = getReplanningStringForOriginalDir(replanningVariant);

		if (deleteOutputDirIfExisting) {
			System.out.println("Deleting output directory if it exists.");
			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		}

		// possibly modify config here

		// String outputDir = String.format("%s/%s/rerun_last_java_iter/beta%d/readFromRandom%d/useRandom%d/", baseOutputDir, replanningVariant, beta, readFromRandom, useRandom);

		System.out.println("Setting output directory to: " + outputDir);
		config.controller().setOutputDirectory(outputDir);

		// set the random seed to use
		config.global().setRandomSeed(useRandom);

		// set vehicles file
		// String vehiclesFile = String.format("../../../no_spillback_beta%d_vehicles.xml", beta);
		//config.vehicles().setVehiclesFile(vehiclesFile);

		// set (input) plans file
		String plansFile = String.format("beta%drandom%d.output_plans.xml.gz", beta, readFromRandom);
		config.plans().setInputFile(plansFile);

		// set network file
		String networkFile = "../../../no_spillback_network.xml";

		config.network().setInputFile(networkFile);

		// disable network route consistency check, as the plans file contains unreachable nodes/links
		config.routing().setNetworkRouteConsistencyCheck(RoutingConfigGroup.NetworkRouteConsistencyCheck.disable);

		// disable replanning; only run one last iteration
		config.controller().setLastIteration(0);

		// manually set time step size based on beta, since the config files are not written correctly (rounded to secs)
		config.qsim().setTimeStepSize(1. / beta);
		// ---

		return config;
	}

	private static @NonNull String getReplanningStringForOriginalDir(String replVar) {
		String replanning_string_for_original_dir;
        switch (replVar	) {
			case "sel-exp10-switch-at80" -> replanning_string_for_original_dir = "2026-05-12-8-42-24_500it_reRouteProba0.1until0.8it_selExpBeta10proba0.9_msaFrom0.8it";
			case "sel-exp1-switch-at80" -> replanning_string_for_original_dir = "2026-05-10-10-2-21_500it_reRouteProba0.1until0.8it_selExpBeta1proba0.9_msaFrom0.8it";
			case "sel-exp1-switch-at50" -> replanning_string_for_original_dir = "2026-05-8-12-16-8_500it_reRouteProba0.1until0.5it_selExpBeta1proba0.9_msaFrom0.5it";
			default -> throw new IllegalArgumentException("Unknown replanning variant: " + replVar);
		}
		return replanning_string_for_original_dir;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		// possibly modify scenario here

        double pcuEquivalents = 1.0 / Math.pow(beta, 2);
        double vehLength = 7.5 / Math.pow(beta, 2);

		// remove the default vehicle type, which has wrong pcu equivalents and length
		//Id<VehicleType> defaultVehicleTypeId = Id.createVehicleTypeId("defaultVehicleType");
		//scenario.getVehicles().removeVehicleType(defaultVehicleTypeId);

		// create a vehicle type with correct pcu equivalents and length
		VehicleType myVehType = scenario.getVehicles().getFactory().createVehicleType(Id.create("defaultVehicleType", VehicleType.class));
		myVehType.setPcuEquivalents(pcuEquivalents);
		myVehType.setLength(vehLength);
		myVehType.setNetworkMode("car");

		scenario.getVehicles().addVehicleType(myVehType);

		// create a vehicle of this type for all agents
		for (Id<Person> personId : scenario.getPopulation().getPersons().keySet()) {
            //Id<Vehicle> current_veh_id;
            //current_veh_id = Id.createVehicleId(personId);

			// remove the current vehicle of the person
			// scenario.getVehicles().getVehicles().remove(current_veh_id);

			// add a new vehicle of the correct type for the person
			scenario.getVehicles().addVehicle(scenario.getVehicles().getFactory().createVehicle(Id.createVehicleId(personId), myVehType));
		}





		// TODO this doesn't help, because it's a parsing error, not a runtime error. That was different in the time step problem apparently.
		// Id<VehicleType> defaultVehicleTypeId = Id.createVehicleTypeId("defaultVehicleType");

		// modify length and pcu equivalents based on beta (since they are not written correctly in the files)
		// scenario.getVehicles().getVehicleTypes().get(defaultVehicleTypeId).setLength(7.5 / Math.pow(beta, 2));
		// scenario.getVehicles().getVehicleTypes().get(defaultVehicleTypeId).setPcuEquivalents(1.0 / Math.pow(beta, 2));


		// ---

	}

	@Override
	protected void prepareControler(Controler controler) {

		// possibly modify controler here

//		controler.addOverridingModule( new OTFVisLiveModule() ) ;
//		controler.addOverridingModule( new SimWrapperModule() ) ;


		// ---
	}
}
