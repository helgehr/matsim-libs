/* *********************************************************************** *
 * project: org.matsim.*
 * PajekWriter1.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.socialnetworks.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import org.matsim.facilities.Facilities;
import org.matsim.facilities.Facility;
import org.matsim.gbl.Gbl;
import org.matsim.plans.Act;
import org.matsim.plans.Knowledge;
import org.matsim.plans.Person;
import org.matsim.plans.Plans;
import org.matsim.socialnetworks.algorithms.FacilitiesFindScenarioMinMaxCoords;
import org.matsim.socialnetworks.socialnet.SocialNetEdge;
import org.matsim.socialnetworks.socialnet.SocialNetwork;
import org.matsim.socialnetworks.statistics.GeoStatistics;
import org.matsim.utils.geometry.CoordI;
import org.matsim.utils.geometry.shared.Coord;
import org.matsim.utils.identifiers.IdI;
import org.matsim.world.Location;
import org.matsim.world.Zone;

import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.Vertex;



public class PajekWriter {

	private CoordI minCoord;
	private CoordI maxCoord;
	private TreeMap<IdI, Integer> pajekIndex= new TreeMap<IdI, Integer>();
	String dir;

	public PajekWriter(String dir, Facilities facilities){
		this.dir=dir;
		//String pjoutdir = Gbl.getConfig().findParam(Gbl.getConfig().SOCNET, Gbl.getConfig().SOCNET_OUT_DIR);
		File pjDir=new File(dir+"pajek/");
		System.out.println("PajekWriter1 make dir "+dir + "pajek/");
		if(!(pjDir.mkdir())&& !pjDir.exists()){
			Gbl.errorMsg("Cannot create directory "+dir+"pajek/");
		}
		Gbl.noteMsg(this.getClass(),"","is a dumb writer for UNDIRECTED nets. Replace it with something that iterates through Persons and call it from SocialNetworksTest.");
		FacilitiesFindScenarioMinMaxCoords fff= new FacilitiesFindScenarioMinMaxCoords();
		fff.run(facilities);
		minCoord = fff.getMinCoord();
		maxCoord = fff.getMaxCoord();
		System.out.println(" PW X_Max ="+maxCoord.getX());
		System.out.println(" PW Y_Max ="+maxCoord.getY());
		System.out.println(" PW X_Min ="+minCoord.getX());
		System.out.println(" PW Y_Min ="+minCoord.getY());

	}

	public void write(ArrayList<SocialNetEdge> links, Plans plans, int iter) {
		BufferedWriter pjout = null;

		// from config

		String pjoutfile = dir+"pajek/test"+iter+".net";
		System.out.println("PajekWriter1 filename "+pjoutfile);

		try {

			pjout = new BufferedWriter(new FileWriter(pjoutfile));
			System.out.println(" Successfully opened pjoutfile "+pjoutfile);

		} catch (final IOException ex) {
		}

		int numPersons = plans.getPersons().values().size();

		try {
//			System.out.print(" *Vertices " + numPersons + " \n");
			pjout.write("*Vertices " + numPersons);
			pjout.newLine();

			Iterator<Person> itPerson = plans.getPersons().values().iterator();
			int iperson = 1;
			while (itPerson.hasNext()) {
				Person p = (Person) itPerson.next();
				final Knowledge know = p.getKnowledge();
				if (know == null) {
					Gbl.errorMsg("Knowledge is not defined!");
				}
				Coord xy = (Coord) ((Act) p.getSelectedPlan().getActsLegs().get(0)).getCoord();
				double x=(xy.getX()-minCoord.getX())/(maxCoord.getX()-minCoord.getX());
				double y=(xy.getY()-minCoord.getY())/(maxCoord.getY()-minCoord.getY());
				pjout.write(iperson + " \"" + p.getId() + "\" "+x +" "+y);
				pjout.newLine();
//				System.out.print(iperson + " " + p.getId() + " ["+xy.getX() +" "+xy.getY()+"]\n");
				pajekIndex.put(p.getId(),iperson);
				iperson++;

			}
			pjout.write("*Edges");
			pjout.newLine();
//			System.out.print("*Edges\n");
			Iterator<SocialNetEdge> itLink = links.iterator();
			while (itLink.hasNext()) {
				SocialNetEdge printLink = (SocialNetEdge) itLink.next();
				int age = iter-printLink.getTimeLastUsed();
				Person printPerson1 = printLink.person1;
				Person printPerson2 = printLink.person2;

				Coord xy1 = (Coord) ((Act) printPerson1.getSelectedPlan().getActsLegs().get(0)).getCoord();
				Coord xy2 = (Coord) ((Act) printPerson2.getSelectedPlan().getActsLegs().get(0)).getCoord();
				double dist = xy1.calcDistance(xy2);

				pjout.write(" " + pajekIndex.get(printPerson1.getId()) + " "+ pajekIndex.get(printPerson2.getId())+" "+dist+" "+age);
//				pjout.write(" " + printPerson1.getId() + " "+ printPerson2.getId());
				pjout.newLine();
//				System.out.print(" " +iter+" "+printLink.getLinkId()+" "+ printPerson1.getId() + " "
//				+ printPerson2.getId() + " "
//				+ printLink.getTimeLastUsed()+"\n");
			}

		} catch (IOException ex1) {
		}

		try {
			pjout.close();
			System.out.println(" Successfully closed pjoutfile "+pjoutfile);
		} catch (IOException ex2) {
		}
		//}
	}
	public void writeGeo(Plans plans, SocialNetwork snet, int iter) {

		GeoStatistics gstat = new GeoStatistics(plans, snet);
		Graph g = gstat.makeJungGraph();

		BufferedWriter pjout = null;

		// from config

		String pjoutfile = dir+"pajek/testGeo"+iter+".net";
		System.out.println("PajekWriter1 Geofilename "+pjoutfile);

		try {

			pjout = new BufferedWriter(new FileWriter(pjoutfile));
			System.out.println(" Successfully opened pjoutfile "+pjoutfile);

		} catch (final IOException ex) {
		}

		int numVertices = g.numVertices();

		try {
			System.out.print("##### Write Geoaggregated Social Network Output");
			System.out.print(" *Vertices " + numVertices + " \n");
			pjout.write("*Vertices " + numVertices);
			pjout.newLine();

			Iterator<Vertex> iVert = g.getVertices().iterator();
			HashMap<Vertex,Location> vertLoc = gstat.getVertexLoc();

			int vertexcounter = 1;
			while (iVert.hasNext()) {
				Vertex v = (Vertex) iVert.next();
				Zone zone = (Zone) vertLoc.get(v);

				Coord xy = (Coord) zone.getCenter();
				double x=(xy.getX()-minCoord.getX())/(maxCoord.getX()-minCoord.getX());
				double y=(xy.getY()-minCoord.getY())/(maxCoord.getY()-minCoord.getY());
				pjout.write(vertexcounter + " \"" + zone.getId() + "\" "+x +" "+y);
				pjout.newLine();
//				System.out.print(iperson + " " + p.getId() + " ["+xy.getX() +" "+xy.getY()+"]\n");
				pajekIndex.put(zone.getId(),vertexcounter);
				vertexcounter++;

			}
			pjout.write("*Edges");
			pjout.newLine();
//			System.out.print("*Edges\n");
			Iterator<Edge> itLink = g.getEdges().iterator();
			while (itLink.hasNext()) {
				Edge printLink = (Edge) itLink.next();
				Location aLoc = gstat.getVertexLoc().get(printLink.getEndpoints().getFirst());
				Location bLoc = gstat.getVertexLoc().get(printLink.getEndpoints().getSecond());

				Coord xy1 = (Coord) aLoc.getCenter();
				Coord xy2 = (Coord) bLoc.getCenter();
				double dist = xy1.calcDistance(xy2);
				double strength = (Double) printLink.getUserDatum("strength");
//				double strength = gstat.getEdgeStrength().get(printLink);
				pjout.write(" " + pajekIndex.get(aLoc.getId()) + " "+ pajekIndex.get(bLoc.getId())+" "+strength);
				pjout.newLine();

			}

		} catch (IOException ex1) {
		}
		try {
			pjout.close();
			System.out.println(" Successfully closed pjoutfile "+pjoutfile);
		} catch (IOException ex2) {
		}

	}
}
