package playground.dressler.ea_flow;

import playground.dressler.Interval.VertexInterval;

public class BFTask {
	final public int time;
	final public VertexInterval ival;
	final public VirtualNode node;
	final public boolean reverse; 
	public int depth;
	
	
	BFTask(VirtualNode node, VertexInterval oldival, boolean rev){
		this.time = oldival.getLowBound();
		this.ival = oldival.copy(); // we don't want this to be a reference!
		this.node = node;
		this.reverse = rev;
		this.depth = 0;
	}
		
	BFTask(VirtualNode node, int time, boolean rev){
		this.time = time;
		this.node = node; 			
		this.ival = null;
		this.reverse = rev;
		this.depth = 0;
	}
	
	Boolean equals(BFTask other){
		// this ignores ival!
		return(this.time == other.time 
				&& this.reverse == other.reverse
				&& this.ival.equals(other.ival)
				&& other.node.equals(this.node));
	}
	
	@Override
	public String toString(){
		return node.toString() + " @ " + time +  " interval " + ival + " reverse " + reverse;
	}
}
