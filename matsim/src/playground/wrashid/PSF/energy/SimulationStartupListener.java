package playground.wrashid.PSF.energy;

import java.util.LinkedList;

import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.events.handler.EventHandler;

import playground.wrashid.PSF.parking.LogParkingTimes;

public class SimulationStartupListener implements StartupListener {

	LinkedList<EventHandler> eventHandler = new LinkedList<EventHandler>();

	public void addEventHandler(EventHandler eventHandler) {
		this.eventHandler.add(eventHandler);
	}

	public void notifyStartup(StartupEvent event) {
		for (int i = 0; i < eventHandler.size(); i++) {
			event.getControler().getEvents().addHandler(eventHandler.get(i));
		}
	}

}
