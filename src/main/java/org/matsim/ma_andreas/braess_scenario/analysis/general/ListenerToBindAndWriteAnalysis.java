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
package org.matsim.ma_andreas.braess_scenario.analysis.general;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;


/**
 * Class to bind the analyze and writing tool to the simulation. 
 * It works for all analyze tools that extend the
 * abstract analyze tool TtAbstractAnalyzeTool.
 * 
 * @author tthunig
 */
public class ListenerToBindAndWriteAnalysis implements IterationEndsListener {

	private static final Logger log = LogManager.getLogger(ListenerToBindAndWriteAnalysis.class);
	
	@Inject
	private Scenario scenario;
	
	@Inject
	private AnalyzedResultsWriter writer;
	
	protected String scriptNameRouteDistribution = "plot_routeDistribution";
	protected String scriptNameRoutesAndTTs = "plot_routesAndTTs";
//	protected String scriptNameRouteDistribution = "plot_routeDistribution2thBraess";
//	protected String scriptNameRoutesAndTTs = "plot_routesAndTTs2thBraess";
	
	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		// write analyzed data
		writer.writeIterationResults(event.getIteration());
		runGnuplotScript(scriptNameRouteDistribution, event.getIteration());

		// handle last iteration
		if (event.getIteration() == scenario.getConfig().controller().getLastIteration()) {
			// close overall writing stream
			writer.closeAllStreams();
			// plot overall iteration results
			runGnuplotScript(scriptNameRoutesAndTTs, event.getIteration());
		}
	}
	
	/**
	 * starts the gnuplot script from the specific iteration directory
	 * 
	 * @param gnuplotScriptName
	 * @param iteration
	 */
	private void runGnuplotScript(String gnuplotScriptName, int iteration){
		String pathToSpecificAnalysisDir = scenario.getConfig().controller().getOutputDirectory() + "ITERS/it." + iteration + "/analysis";
		// five levels works for the single runs in runs-svn/braess/infPhases/date_properties/ITERS...
//		String relativePathToGnuplotScript = "../../../../../analysis/" + gnuplotScriptName  + ".p";
		// nashFlowDir/matsimOutput/braess/date_properties/scaleLevel/randomSeed/ITERS... should be eight?!
//		String relativePathToGnuplotScript = "../../../../../../../../analysis/" + gnuplotScriptName  + ".p"; // for parametric braess runs from project directory
		String relativePathToGnuplotScript = "../../../../../../../analysis/" + gnuplotScriptName  + ".p"; // for parametric braess runs from runs svn
//		String relativePathToGnuplotScript = "../../../../../analysis/" + gnuplotScriptName  + ".p"; // for kth braess run
		
		log.info("execute command: cd " + pathToSpecificAnalysisDir);
		log.info("and afterwards: gnuplot " + relativePathToGnuplotScript);
		
		GnuplotUtils.runGnuplotScript(pathToSpecificAnalysisDir, relativePathToGnuplotScript);
	}

}
