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
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.lanes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class to create network and lanes for the breass scenario.
 * 
 * You may choose between simulating inflow capacity or not. Whereby simulating
 * inflow capacity means that one additional short link is added in front of
 * link 2-3, 2-4 and 4-5. (Inflow capacity at 3-4 and 3-5 is not necessary (i.e.
 * flow capacity can not be exceeded at this links) because they only have one
 * incoming link with the same flow capacity.)
 * 
 * @author tthunig
 * 
 */
public final class TtCreateBraessNetworkAndLanes {
	
	private static final Logger log = LogManager
			.getLogger(TtCreateBraessNetworkAndLanes.class);
	
	public enum LaneType{
		NONE, TRIVIAL, REALISTIC
	}
	
	private Scenario scenario;
	
	private boolean simulateInflowCap = false;
	private boolean middleLinkExists = true;
	private LaneType laneType = LaneType.NONE; 
	private double numberOfPersonsPerSec;
	
	// capacity of the links that all agents have to use
	private double capFirstLast; // [veh/h]
	// capacity of middle link (use the default capacity if it is 0.0)
	private double capZ; // [veh/h]
	// capacity of slow links (24, 35)
	private double capSlow; // [veh/h]
	// capacity of fast links (23, 45)
	private double capFast; // [veh/h]
	// link length for the inflow links
	private double inflowLinkLength; // [m]
	// link length for all links with big travel time, i.e. long links that bypass the bottleneck
	private double linkLengthBig; // [m]
	// link length for all links on the middle route
	private double linkLengthSmall1; // [m]
	private double linkLengthMid; // [m]
	private double linkLengthSmall2; // [m]
	// link length for the initial and last link
	private double linkLengthFirstLast;
	// travel time for the middle link
	private double linkTTMid;
	// travel time for the middle route links
	private double linkTTSmall; // [s]
	// travel time for the two remaining outer route links, i.e. the ones that bypass the bottleneck
	private double linkTTBig1; // [s]
	private double linkTTBig2; // [s]
	// travel time for inflow links and links that all agents have to use
	private double minimalLinkTT; // [s]


	public TtCreateBraessNetworkAndLanes(Scenario scenario) {		
		this.scenario = scenario;
	}

	/**
	 * Creates the network for the Breass scenario.
	 */
	public void createNetworkAndLanes(){
		
		initNetworkParams();
		
		createNetwork();
		
		if (laneType.equals(LaneType.TRIVIAL))
			createTrivialLanes();
		if (laneType.equals(LaneType.REALISTIC))
			createRealisticLanes();
		if (laneType.equals(LaneType.NONE))
			log.info("No lanes are used");
	}

	private void initNetworkParams() {
		/* use defaults if capacities are not set yet */
		if (capFirstLast == 0.0)
			capFirstLast = numberOfPersonsPerSec * 3600;
		if (capSlow == 0.0)
			capSlow = numberOfPersonsPerSec * 3600 / 2;
		if (capFast == 0.0)
			capFast = numberOfPersonsPerSec * 3600 / 2;
		if (capZ == 0.0)
			capZ = capSlow;

//		// non-alternating braess with spillback
//		inflowLinkLength = 7.5 * 1;
//		linkLengthSmall1 = 7.5 * 6; //10000;
//		linkLengthMid = 7.5 * 6; //10000;
//		linkLengthSmall2 = 7.5 * 6; //10000;
//		linkLengthBig = 7.5 * 60; //10000;
//		linkLengthFirstLast = 7.5 * 10000;
//		linkTTMid = 5;
//		linkTTSmall = 5;
//		linkTTBig1 = 36.01; // unten
//		linkTTBig2 = 23.01; // oben
//		minimalLinkTT = 5;
		
		// non-alternating braess without spillback
		inflowLinkLength = 7.5 * 1;
		linkLengthSmall1 = 7.5 * 10000;
		linkLengthMid = 7.5 * 10000;
		linkLengthSmall2 = 7.5 * 10000;
		linkLengthBig = 7.5 * 10000;
		linkLengthFirstLast = 7.5 * 10000;
		linkTTMid = 5;
		linkTTSmall = 5;
		linkTTBig1 = 40; // unten
		linkTTBig2 = 40; // oben
		minimalLinkTT = 5;
		
		// alternating nash flow 
		// (devided by 6 because example has originally 6 inflow)
//		inflowLinkLength = 7.5 * 1 / 6;
//		linkLengthSmall1 = 7.5 * 60 / 6;
//		linkLengthMid = 7.5 * 30 / 6;
//		linkLengthSmall2 = 7.5 * 10000 / 6;
//		linkLengthBig = 7.5 * 10000 / 6;
//		linkLengthFirstLast = 7.5 * 10000 / 6;
//		linkTTMid = 1;
//		linkTTSmall = 1;
//		linkTTBig1 = 42;
//		linkTTBig2 = 45;
//		minimalLinkTT = 1;
//		capFirstLast = 6 * 3600 / 6;
//		capSlow = 6 * 3600 / 6;
//		capFast = 3 * 3600 / 6;
//		capZ = 2 * 3600 / 6;
	}

	private void createNetwork() {
		Network net = this.scenario.getNetwork();
		NetworkFactory fac = net.getFactory();

		// create nodes
		net.addNode(fac.createNode(Id.createNodeId(0),
				new Coord(-200, 200)));
		net.addNode(fac.createNode(Id.createNodeId(1),
				new Coord(0, 200)));
		net.addNode(fac.createNode(Id.createNodeId(2),
				new Coord(200, 200)));
		net.addNode(fac.createNode(Id.createNodeId(3),
				new Coord(400, 400)));
		net.addNode(fac.createNode(Id.createNodeId(4),
				new Coord(400, 0)));
		net.addNode(fac.createNode(Id.createNodeId(5),
				new Coord(600, 200)));
		net.addNode(fac.createNode(Id.createNodeId(6),
				new Coord(800, 200)));

		if (simulateInflowCap){
			net.addNode(fac.createNode(Id.createNodeId(23),
					new Coord(250, 250)));
			net.addNode(fac.createNode(Id.createNodeId(24),
					new Coord(250, 150)));
			net.addNode(fac.createNode(Id.createNodeId(45),
					new Coord(450, 50)));
		}
		
		// create links
		Link l = fac.createLink(Id.createLinkId("0_1"),
				net.getNodes().get(Id.createNodeId(0)),
				net.getNodes().get(Id.createNodeId(1)));
		setLinkAttributes(l, capFirstLast, linkLengthFirstLast, minimalLinkTT);
		net.addLink(l);
		
		l = fac.createLink(Id.createLinkId("1_2"),
				net.getNodes().get(Id.createNodeId(1)),
				net.getNodes().get(Id.createNodeId(2)));
		setLinkAttributes(l, capFirstLast, linkLengthFirstLast, minimalLinkTT);	
		net.addLink(l);
		
		if (simulateInflowCap){
			l = fac.createLink(Id.createLinkId("2_23"),
					net.getNodes().get(Id.createNodeId(2)),
					net.getNodes().get(Id.createNodeId(23)));
			setLinkAttributes(l, capFast, inflowLinkLength, minimalLinkTT);
			net.addLink(l);
			
			l = fac.createLink(Id.createLinkId("23_3"),
					net.getNodes().get(Id.createNodeId(23)),
					net.getNodes().get(Id.createNodeId(3)));
			setLinkAttributes(l, capFast, linkLengthSmall1, linkTTSmall - minimalLinkTT);
			net.addLink(l);
		} else {
			l = fac.createLink(Id.createLinkId("2_3"),
					net.getNodes().get(Id.createNodeId(2)),
					net.getNodes().get(Id.createNodeId(3)));
			setLinkAttributes(l, capFast, linkLengthSmall1, linkTTSmall);
			net.addLink(l);
		}
		
		if (simulateInflowCap){
			l = fac.createLink(Id.createLinkId("2_24"),
					net.getNodes().get(Id.createNodeId(2)),
					net.getNodes().get(Id.createNodeId(24)));
			setLinkAttributes(l, capSlow, inflowLinkLength, minimalLinkTT);
			net.addLink(l);
			
			l = fac.createLink(Id.createLinkId("24_4"),
					net.getNodes().get(Id.createNodeId(24)),
					net.getNodes().get(Id.createNodeId(4)));
			setLinkAttributes(l, capSlow, linkLengthBig, linkTTBig1 - minimalLinkTT);
			net.addLink(l);
		} else {
			l = fac.createLink(Id.createLinkId("2_4"),
					net.getNodes().get(Id.createNodeId(2)),
					net.getNodes().get(Id.createNodeId(4)));
			setLinkAttributes(l, capSlow, linkLengthBig, linkTTBig1);
			net.addLink(l);
		}
		
		if (this.middleLinkExists){
			l = fac.createLink(Id.createLinkId("3_4"),
				net.getNodes().get(Id.createNodeId(3)),
				net.getNodes().get(Id.createNodeId(4)));
			setLinkAttributes(l, capZ, linkLengthMid, linkTTMid);
			net.addLink(l);
		}
	
		l = fac.createLink(Id.createLinkId("3_5"),
				net.getNodes().get(Id.createNodeId(3)),
				net.getNodes().get(Id.createNodeId(5)));
		setLinkAttributes(l, capSlow, linkLengthBig, linkTTBig2);
		net.addLink(l);
		
		if (simulateInflowCap){
			l = fac.createLink(Id.createLinkId("4_45"),
					net.getNodes().get(Id.createNodeId(4)),
					net.getNodes().get(Id.createNodeId(45)));
			setLinkAttributes(l, capFast, inflowLinkLength, minimalLinkTT);
			net.addLink(l);
			
			l = fac.createLink(Id.createLinkId("45_5"),
					net.getNodes().get(Id.createNodeId(45)),
					net.getNodes().get(Id.createNodeId(5)));
			setLinkAttributes(l, capFast, linkLengthSmall2, linkTTSmall - minimalLinkTT);
			net.addLink(l);
		}
		else{
			l = fac.createLink(Id.createLinkId("4_5"),
					net.getNodes().get(Id.createNodeId(4)),
					net.getNodes().get(Id.createNodeId(5)));
			setLinkAttributes(l, capFast, linkLengthSmall2, linkTTSmall);
			net.addLink(l);
		}
		
		l = fac.createLink(Id.createLinkId("5_6"),
				net.getNodes().get(Id.createNodeId(5)),
				net.getNodes().get(Id.createNodeId(6)));
		setLinkAttributes(l, capFirstLast, linkLengthFirstLast, minimalLinkTT);
		net.addLink(l);
	}

	private static void setLinkAttributes(Link link, double capacity,
			double length, double travelTime) {
		
		link.setCapacity(capacity);
		link.setLength(length);
		// agents have to reach the end of the link before the time step ends to
		// be able to travel forward in the next time step (matsim time step logic)
		link.setFreespeed(link.getLength() / (travelTime - 1./10000));
	}

	/**
	 * creates a trivial lane for every link
	 */
	private void createTrivialLanes() {
		
		Lanes laneDef20 = this.scenario.getLanes();
		LanesFactory fac = laneDef20.getFactory();
		
		for (Link link: scenario.getNetwork().getLinks().values()){
			// create a trivial lane for every link that has outgoing links
			if (link.getToNode().getOutLinks() != null && !link.getToNode().getOutLinks().isEmpty()) {
				LanesToLinkAssignment linkAssignment = fac.createLanesToLinkAssignment(link.getId());
				// add all outgoing links as toLinks
				List<Id<Link>> toLinkList = new ArrayList<>(link.getToNode().getOutLinks().keySet());
				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create(link.getId() + ".ol", Lane.class), link.getCapacity(),
						link.getLength(), 0, 1, toLinkList, null);
				laneDef20.addLanesToLinkAssignment(linkAssignment);
			}
		}
	}

	/**
	 * creates a lane for every turning direction, i.e. for every signal at a link
	 */
	private void createRealisticLanes() {
		
		Lanes laneDef20 = this.scenario.getLanes();
		LanesFactory fac = laneDef20.getFactory();

		// create link assignment of link 1_2
		LanesToLinkAssignment linkAssignment = fac
				.createLanesToLinkAssignment(Id.createLinkId("1_2"));

		LanesUtils.createAndAddLane(linkAssignment, fac,
				Id.create("1_2.ol", Lane.class), capFirstLast,
				linkLengthFirstLast, 0, 1, null, 
				Arrays.asList(Id.create("1_2.l", Lane.class),
				Id.create("1_2.r", Lane.class)));
		
		if (simulateInflowCap) {
			LanesUtils.createAndAddLane(linkAssignment, fac,
					Id.create("1_2.l", Lane.class), capFirstLast,
					linkLengthFirstLast / 2, -1, 1,
					Collections.singletonList(Id.createLinkId("2_23")),	null);
			LanesUtils.createAndAddLane(linkAssignment, fac,
					Id.create("1_2.r", Lane.class), capFirstLast,
					linkLengthFirstLast / 2, 1, 1,
					Collections.singletonList(Id.createLinkId("2_24")), null);
		} else {
			LanesUtils.createAndAddLane(linkAssignment, fac,
					Id.create("1_2.l", Lane.class), capFirstLast,
					linkLengthFirstLast / 2, -1, 1,
					Collections.singletonList(Id.createLinkId("2_3")), null);
			LanesUtils.createAndAddLane(linkAssignment, fac,
					Id.create("1_2.r", Lane.class), capFirstLast,
					linkLengthFirstLast / 2, 1, 1,
					Collections.singletonList(Id.createLinkId("2_4")), null);
		}	
		
		laneDef20.addLanesToLinkAssignment(linkAssignment);
		
		// no lanes on 2_3 (or 23_3) are needed if 3_4 doesn't exist
		if (this.middleLinkExists) {
			// create link assignment of link 2_3 (or 23_3 if inflow capacity is
			// simulated)
			if (simulateInflowCap) {
				linkAssignment = fac.createLanesToLinkAssignment(Id
						.createLinkId("23_3"));

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("23_3.ol", Lane.class), capFast,
						linkLengthSmall1, 0,	1, null,
						Arrays.asList(Id.create("23_3.f", Lane.class),
								Id.create("23_3.r", Lane.class)));

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("23_3.f", Lane.class), capFast,
						linkLengthSmall1 / 2, 0, 1,
						Collections.singletonList(Id.createLinkId("3_5")), null);

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("23_3.r", Lane.class), capFast,
						linkLengthSmall1 / 2, 1, 1,
						Collections.singletonList(Id.createLinkId("3_4")), null);

				laneDef20.addLanesToLinkAssignment(linkAssignment);
			} else {
				linkAssignment = fac.createLanesToLinkAssignment(Id
						.createLinkId("2_3"));

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("2_3.ol", Lane.class), capFast,
						linkLengthSmall1, 0,	1, null,
						Arrays.asList(Id.create("2_3.f", Lane.class),
								Id.create("2_3.r", Lane.class)));

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("2_3.f", Lane.class), capFast,
						linkLengthSmall1 / 2, 0, 1,
						Collections.singletonList(Id.createLinkId("3_5")), null);

				LanesUtils.createAndAddLane(linkAssignment, fac,
						Id.create("2_3.r", Lane.class), capFast,
						linkLengthSmall1 / 2, 1, 1,
						Collections.singletonList(Id.createLinkId("3_4")), null);

				laneDef20.addLanesToLinkAssignment(linkAssignment);
			}
		}
	}

	/**
	 * Sets the flag for simulating inflow capacity.
	 * 
	 * If true, links 2_3, 2_4 and 4_5 are divided into 2 links: a small one at
	 * the beginning that simulates an inflow capacity at the link and a longer
	 * one that preserves the other properties of the link
	 * 
	 * @param simulateInflowCap
	 */
	public void setSimulateInflowCap(boolean simulateInflowCap) {
		this.simulateInflowCap = simulateInflowCap;
	}

	public void setMiddleLinkExists(boolean middleLinkExists) {
		this.middleLinkExists = middleLinkExists;
	}

	public void setLaneType(LaneType laneType) {
		this.laneType = laneType;
	}

	public void writeNetworkAndLanes(String directory) {
		new NetworkWriter(scenario.getNetwork()).write(directory + "network.xml");
		if (!laneType.equals(LaneType.NONE)) new LanesWriter(scenario.getLanes()).write(directory + "lanes.xml");
	}

	public void setNumberOfPersonsPerSec(double numberOfPersons) {
		this.numberOfPersonsPerSec = numberOfPersons;
	}

	public void setCapZ(double capZ) {
		this.capZ = capZ;
	}

	public void setCapSlow(double capSlow) {
		this.capSlow = capSlow;
	}

	public void setCapFast(double capFast) {
		this.capFast = capFast;
	}

	public void setCapFirstLast(double capFirstLast) {
		this.capFirstLast = capFirstLast;
	}
}
