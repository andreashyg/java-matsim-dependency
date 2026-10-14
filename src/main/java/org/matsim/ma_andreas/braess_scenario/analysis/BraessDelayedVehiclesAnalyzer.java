package org.matsim.ma_andreas.braess_scenario.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.ArrayMap;
import org.matsim.vehicles.Vehicle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This class determines how many agents (in pcu equivalents) are delayed on each link of the braess network.
 *
 * This tool does not (!) analyze the delay between person departure and vehicle enters traffic
 * (because it is not relevant in our specific example).
 */
public class BraessDelayedVehiclesAnalyzer implements LinkEnterEventHandler, LinkLeaveEventHandler,
        VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {

    private final int ANALYSES_END_TIME = 1000; // for 100s of simulation time, 250s is a good value

    private Network network;
    private PrintStream writingStreamDelayedVehicles;
    private double pcuEquivalents;
    private double simTimeStepSize;

    private Map<Double, Map<Id<Link>, Double>> delayedVehiclesPerLinkPerTime = new HashMap<>();

    private Map<Id<Vehicle>, Double> veh2earliestLinkExitTime = new HashMap<>();

    public BraessDelayedVehiclesAnalyzer(Network network, String runPathToLastItDir, int scaleBetaInverse) {
        this.network = network;

        this.pcuEquivalents = 1.0;
        // alpha=beta^2
        int scaleAlphaInverse = scaleBetaInverse * scaleBetaInverse;
        // scale the size of the vehicles down
        pcuEquivalents /= scaleAlphaInverse;

        this.simTimeStepSize = 1.0;
        simTimeStepSize /= scaleBetaInverse;

        for (double time=0; time <=ANALYSES_END_TIME; time += simTimeStepSize) {
            Map<Id<Link>, Double> init_delayedVehiclesPerLink = new ArrayMap<>();
            // initialize link queue entries for all braess links
            init_delayedVehiclesPerLink.put(Id.createLinkId("0_1"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("1_2"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("2_3"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("2_4"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("3_4"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("3_5"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("4_5"), Double.valueOf(0));
            init_delayedVehiclesPerLink.put(Id.createLinkId("5_6"), Double.valueOf(0));

            delayedVehiclesPerLinkPerTime.put(time, init_delayedVehiclesPerLink);
        }

        createWritingStream(runPathToLastItDir + "analysis/delayedVehiclesPerLink.txt");
    }

    private void createWritingStream(String filePath) {
        try {
            writingStreamDelayedVehicles = new PrintStream(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        // write header
        StringBuffer delayedVehiclesHeader = new StringBuffer("time [s]");
        for (Id<Link> linkId : delayedVehiclesPerLinkPerTime.get(Double.valueOf(0)).keySet()) {
            delayedVehiclesHeader.append(" \tdelayed vehicles link" + linkId + " [vol]");
        }
        writingStreamDelayedVehicles.println(delayedVehiclesHeader);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        Link link = network.getLinks().get(linkEnterEvent.getLinkId());
        // calculate earliest link exit time
        double freespeedTt = link.getLength() / link.getFreespeed();
        // this is the earliest time where matsim sets the agent to the next link
        // (rounded up to the next sim step, except it is exactly at a sim step)
        // (with time step size 1 it is Math.floor(freespeedTt + 1) )
        double matsimFreespeedTT = (freespeedTt + simTimeStepSize) - (freespeedTt % simTimeStepSize);
        // save earliest exit time
        veh2earliestLinkExitTime.put(linkEnterEvent.getVehicleId(), linkEnterEvent.getTime() + matsimFreespeedTT);
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent vehicleEntersTrafficEvent) {
        // for the first link every vehicle needs one time step without delay
        veh2earliestLinkExitTime.put(vehicleEntersTrafficEvent.getVehicleId(), vehicleEntersTrafficEvent.getTime() + simTimeStepSize);
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
        // if vehicle was delayed on that link, add it to the delayed vehicles map for all respective time steps
        double earliestExitTime = veh2earliestLinkExitTime.remove(linkLeaveEvent.getVehicleId());
        while (linkLeaveEvent.getTime() > earliestExitTime) {
            double oldDelayedVehicles = delayedVehiclesPerLinkPerTime.get(earliestExitTime).get(linkLeaveEvent.getLinkId());
            delayedVehiclesPerLinkPerTime.get(earliestExitTime).put(linkLeaveEvent.getLinkId(), oldDelayedVehicles + pcuEquivalents);
            earliestExitTime+=simTimeStepSize;
        }
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent) {
        // if vehicle was delayed on that link, add it to the delayed vehicles map for all respective time steps
        double earliestExitTime = veh2earliestLinkExitTime.remove(vehicleLeavesTrafficEvent.getVehicleId());
        while (vehicleLeavesTrafficEvent.getTime() > earliestExitTime) {
            double oldDelayedVehicles = delayedVehiclesPerLinkPerTime.get(earliestExitTime).get(vehicleLeavesTrafficEvent.getLinkId());
            delayedVehiclesPerLinkPerTime.get(earliestExitTime).put(vehicleLeavesTrafficEvent.getLinkId(), oldDelayedVehicles + pcuEquivalents);
            earliestExitTime+=simTimeStepSize;
        }
    }

    public void writeAnalysis() {
        // write complete map out (only header written before)
        // for analysis we need that all files have the same length, so, extend here until sec ANALYSES_END_TIME
        for (double time=0; time <=ANALYSES_END_TIME; time += simTimeStepSize) {
            writeLineToStream(time);
        }
        writingStreamDelayedVehicles.close();
    }

    private void writeLineToStream(double time) {
		StringBuffer line = new StringBuffer();
		line.append(time);
        for (Id<Link> linkId : delayedVehiclesPerLinkPerTime.get(time).keySet()) {
            line.append(" \t" + delayedVehiclesPerLinkPerTime.get(time).get(linkId));
        }
		writingStreamDelayedVehicles.println(line.toString());
	}
}
