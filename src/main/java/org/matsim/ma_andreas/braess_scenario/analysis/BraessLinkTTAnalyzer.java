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

public class BraessLinkTTAnalyzer implements LinkEnterEventHandler, LinkLeaveEventHandler,
        VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {

    private final int ANALYSES_END_TIME = 1000; // for 100s of simulation time, 250s is a good value

    private PrintStream writingStreamTT;
    private double simTimeStepSize;
    private final Network network;

    private Map<Double, Map<Id<Link>, Double>> summedlinkTTsPerLinkEnter = new ArrayMap<>();
    private Map<Double, Map<Id<Link>, Integer>> noEnteringVehiclesPerLinkPerTime = new ArrayMap<>();
    private Map<Id<Link>, Double> freeFlowTT = new ArrayMap<>();

    private Map<Id<Vehicle>, Double> lastVehicleEnterTime = new ArrayMap<>();

    public BraessLinkTTAnalyzer(Network network, String runPathToLastItDir, int scaleBetaInverse) {
        this.network = network;
        this.simTimeStepSize = 1.0;
        simTimeStepSize /= scaleBetaInverse;

        for (double time=0; time <=ANALYSES_END_TIME; time += simTimeStepSize) {
            Map<Id<Link>, Double> init_summedLinkTTsPerLink = new ArrayMap<>();
            // initialize link tt entries for all braess links
            init_summedLinkTTsPerLink.put(Id.createLinkId("0_1"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("1_2"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("2_3"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("2_4"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("3_4"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("3_5"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("4_5"), Double.valueOf(0));
            init_summedLinkTTsPerLink.put(Id.createLinkId("5_6"), Double.valueOf(0));
            summedlinkTTsPerLinkEnter.put(time, init_summedLinkTTsPerLink);

            Map<Id<Link>, Integer> init_noDeparturesPerLink = new ArrayMap<>();
            // initialize link tt entries for all braess links
            init_noDeparturesPerLink.put(Id.createLinkId("0_1"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("1_2"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("2_3"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("2_4"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("3_4"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("3_5"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("4_5"), Integer.valueOf(0));
            init_noDeparturesPerLink.put(Id.createLinkId("5_6"), Integer.valueOf(0));
            noEnteringVehiclesPerLinkPerTime.put(time, init_noDeparturesPerLink);
        }

        initializeFreeFlowTT(Id.createLinkId("0_1"));
        initializeFreeFlowTT(Id.createLinkId("1_2"));
        initializeFreeFlowTT(Id.createLinkId("2_3"));
        initializeFreeFlowTT(Id.createLinkId("2_4"));
        initializeFreeFlowTT(Id.createLinkId("3_4"));
        initializeFreeFlowTT(Id.createLinkId("3_5"));
        initializeFreeFlowTT(Id.createLinkId("4_5"));
        initializeFreeFlowTT(Id.createLinkId("5_6"));

        createWritingStream(runPathToLastItDir + "analysis/travelTimesPerLinkEnter.txt");
    }

    private void initializeFreeFlowTT(Id<Link> linkId) {
        Link link = network.getLinks().get(linkId);
        // calculate earliest link exit time
        double freespeedTt = link.getLength() / link.getFreespeed();
        // this is the earliest time where matsim sets the agent to the next link
        // (rounded up to the next sim step, except it is exactly at a sim step)
        // (with time step size 1 it is Math.floor(freespeedTt + 1) )
        double matsimFreespeedTT = (freespeedTt + simTimeStepSize) - (freespeedTt % simTimeStepSize);
        // save this free flow tt
        freeFlowTT.put(linkId, matsimFreespeedTT);
    }

    private void createWritingStream(String filePath) {
        try {
            writingStreamTT = new PrintStream(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        // write header
        StringBuffer ttHeader = new StringBuffer("link enter time [s]");
        for (Id<Link> linkId : summedlinkTTsPerLinkEnter.get(Double.valueOf(0)).keySet()) {
            ttHeader.append(" \ttt link" + linkId + " [s]");
        }
        writingStreamTT.println(ttHeader);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        handleVehicleEnter(linkEnterEvent.getTime(), linkEnterEvent.getVehicleId(), linkEnterEvent.getLinkId());
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent vehicleEntersTrafficEvent) {
        handleVehicleEnter(vehicleEntersTrafficEvent.getTime(), vehicleEntersTrafficEvent.getVehicleId(),
                vehicleEntersTrafficEvent.getLinkId());
    }

    private void handleVehicleEnter(double time, Id<Vehicle> vehicleId, Id<Link> linkId) {
        // save the vehicles link enter time
        lastVehicleEnterTime.put(vehicleId, time);
        // add a count for this link enter time
        Integer previousCount = noEnteringVehiclesPerLinkPerTime.get(time).get(linkId);
        noEnteringVehiclesPerLinkPerTime.get(time).put(linkId, previousCount + 1 );
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
        handleVehicleLeave(linkLeaveEvent.getTime(), linkLeaveEvent.getVehicleId(), linkLeaveEvent.getLinkId());
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent) {
        handleVehicleLeave(vehicleLeavesTrafficEvent.getTime(), vehicleLeavesTrafficEvent.getVehicleId(),
                vehicleLeavesTrafficEvent.getLinkId());
    }

    private void handleVehicleLeave(double time, Id<Vehicle> vehicleId, Id<Link> linkId) {
        // calculate vehicles travel time, save it for time step of link enter
        Double vehLinkEnter = lastVehicleEnterTime.get(vehicleId);
        Double tt = time - vehLinkEnter;
        Double previousSum = summedlinkTTsPerLinkEnter.get(vehLinkEnter).get(linkId);
        summedlinkTTsPerLinkEnter.get(vehLinkEnter).put(linkId, previousSum + tt);
    }

    public void writeAnalysis() {
        // write complete map out (only header written before)
        // for analysis we need that all files have the same length, so, extend here until sec ANALYSES_END_TIME
        Map<Id<Link>, Double> avgTtLastTimeStep = new HashMap<>();
        for (double time=0; time <=ANALYSES_END_TIME; time += simTimeStepSize) {
            StringBuffer line = new StringBuffer();
            line.append(time);
            for (Id<Link> linkId : summedlinkTTsPerLinkEnter.get(time).keySet()) {
                int departures = noEnteringVehiclesPerLinkPerTime.get(time).get(linkId);
                if (departures == 0) {
                    if (avgTtLastTimeStep.containsKey(linkId)) {
                        avgTtLastTimeStep.put(linkId, avgTtLastTimeStep.get(linkId) - simTimeStepSize);
                    } else {
                        avgTtLastTimeStep.put(linkId, freeFlowTT.get(linkId));
                    }
                    line.append(" \t" + Math.max(freeFlowTT.get(linkId), avgTtLastTimeStep.get(linkId)));
                } else {
                    double avgTtThisTimeStep = summedlinkTTsPerLinkEnter.get(time).get(linkId) / departures;
                    avgTtLastTimeStep.put(linkId, avgTtThisTimeStep);
                    line.append(" \t" + avgTtThisTimeStep);
                }
            }
            writingStreamTT.println(line.toString());
        }
        writingStreamTT.close();
    }
}
