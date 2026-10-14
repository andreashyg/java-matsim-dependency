package org.matsim.ma_andreas.braess_scenario.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.ArrayMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Map;

/**
 * This class determines how many agents (in pcu equivalents) are on each links of the braess network.
 * Note, that this explicitly does not only mean the delayed agents, but all agents on the link!
 */
public class BraessLinkQueueLengthAnalyzer implements LinkEnterEventHandler, LinkLeaveEventHandler,
        PersonDepartureEventHandler, VehicleLeavesTrafficEventHandler {

    private PrintStream writingStreamQueues;
    private double pcuEquivalents;
    private double simTimeStepSize;

    private double lastEventTime = 0;
    private Map<Id<Link>, Double> linkQueues = new ArrayMap<>();

    public BraessLinkQueueLengthAnalyzer(String runPathToLastItDir, int scaleBetaInverse) {
        this.pcuEquivalents = 1.0;
        // alpha=beta^2
        int scaleAlphaInverse = scaleBetaInverse * scaleBetaInverse;
        // scale the size of the vehicles down
        pcuEquivalents /= scaleAlphaInverse;

        this.simTimeStepSize = 1.0;
        simTimeStepSize /= scaleBetaInverse;

        // initialize link queue entries for all braess links
        linkQueues.put(Id.createLinkId("0_1"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("1_2"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("2_3"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("2_4"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("3_4"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("3_5"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("4_5"), Double.valueOf(0));
        linkQueues.put(Id.createLinkId("5_6"), Double.valueOf(0));

        createWritingStream(runPathToLastItDir + "analysis/queueLength.txt");
    }

    private void createWritingStream(String filePath) {
        try {
            writingStreamQueues = new PrintStream(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        // write header
        StringBuffer runQueueHeader = new StringBuffer("time [s]");
        for (Id<Link> linkId : linkQueues.keySet()) {
            runQueueHeader.append(" \tqueue link" + linkId + " [vol]");
        }
        writingStreamQueues.println(runQueueHeader);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        hasTimePassed(linkEnterEvent.getTime());
        // add one pcu to the link queue
        linkQueues.put(linkEnterEvent.getLinkId(), linkQueues.get(linkEnterEvent.getLinkId()) + pcuEquivalents);
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
        hasTimePassed(linkLeaveEvent.getTime());
        // substract one pcu from the link queue
        linkQueues.put(linkLeaveEvent.getLinkId(), linkQueues.get(linkLeaveEvent.getLinkId()) - pcuEquivalents);
    }

    @Override
    public void handleEvent(PersonDepartureEvent personDepartureEvent) {
        hasTimePassed(personDepartureEvent.getTime());
        // add one pcu to the link queue
        // note: use person departure instead of vehicle enters traffic to be able to see all delay in the queue file
        linkQueues.put(personDepartureEvent.getLinkId(), linkQueues.get(personDepartureEvent.getLinkId()) + pcuEquivalents);
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent) {
        hasTimePassed(vehicleLeavesTrafficEvent.getTime());
        // substract one pcu from the link queue
        linkQueues.put(vehicleLeavesTrafficEvent.getLinkId(), linkQueues.get(vehicleLeavesTrafficEvent.getLinkId()) - pcuEquivalents);
    }

    public void closeStream() {
        // write everything for the last second
        // note: everything until the second last second has already been written with the last vehicleLeavesTrafficEvent
        // for analysis we need that all queue length files have the same length, so, extend here until sec 225
        for (; lastEventTime <= 250; lastEventTime += simTimeStepSize) {
            writeLineToStream(lastEventTime);
        }

        writingStreamQueues.close();
    }

    private void hasTimePassed(double currentTime) {
        if (currentTime > lastEventTime) {
            // write queue length of all (might be multiple!) intermediate time steps to the stream
            for (;lastEventTime < currentTime; lastEventTime += simTimeStepSize) {
                writeLineToStream(lastEventTime);
            }
            lastEventTime = currentTime;
        }
    }

    private void writeLineToStream(double time) {
		StringBuffer line = new StringBuffer();
		line.append(time);
        for (Id<Link> linkId : linkQueues.keySet()) {
            line.append(" \t" + linkQueues.get(linkId));
        }
		writingStreamQueues.println(line.toString());
	}
}
