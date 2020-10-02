import java.util.*;
import java.util.concurrent.Semaphore;

/* Class to represent a node. Each node must run on its own thread.*/

public class Node extends Thread {

	private int id;
	private boolean participant = false;
	private boolean leader = false;
	public boolean terminate_interrupt = false;
	private Network network;
	private List<String> broadcast_ids;

	public Semaphore semaphore1;
	public Semaphore semaphore2;
	public Semaphore semaphore3;
	public Semaphore semaphore4;
	
	// Neighbouring nodes
	public List<Node> myNeighbours;
	public Node next;

	public boolean startElection = false;
	public boolean terminate = false;

	// Queues for the incoming messages
	public List<String> incomingMsg;
	
	public Node(int id,  Network network){
	
		this.id = id;
		this.network = network;
		
		myNeighbours = new ArrayList<>();
		incomingMsg = new ArrayList<>();
		broadcast_ids = new ArrayList<>();
	}
	
	// Basic methods for the Node class
	
	public int getNodeId() {
		return id;
	}
		
	public List<Node> getNeighbors() {
		return myNeighbours;
	}

	public void setNext(Node n){
		next = n;
	}

	public Node getNext(){
		return next;
	}

	public void addNeighbour(Node n) {
		if (!myNeighbours.contains(n)){
			myNeighbours.add(n);
		}
	}

	// Process incoming message and craft outgoing message
	public void receiveMsg(String m) {
		String[] msg = m.split(" ", 0);
		if (m.equals("StartLeaderElection")){
			sendMsg("ELECT " + getNodeId());
			participant = true;
		}
		else if(msg[0].equals("FORWARD")){
			int inc = Integer.parseInt(msg[1]);
			if (!participant){
				participant = true;
				sendMsg("FORWARD " + Integer.max(inc, getNodeId()));
			}
			else{
				if (inc > getNodeId()){
					sendMsg("FORWARD " + inc);
				}
				else if (inc == getNodeId()){
					sendMsg("LEADER " + getNodeId());
					participant = false;
					leader = true;
				}
			}
		}
		else if(msg[0].equals("ELECT")){
			int inc = Integer.parseInt(msg[1]);
			if (!participant){
				participant = true;
				sendMsg("FORWARD " + Integer.max(inc, getNodeId()));
			}
			else if (inc > getNodeId()){
					sendMsg("FORWARD " + inc);
				}
		}
		else if(msg[0].equals("LEADER")){
			if (Integer.parseInt(msg[1]) != getNodeId()){
				sendMsg(m);
			}
			participant = false;
		}
		else if(msg[0].equals("STOP")){
			broadcast_ids.add(msg[1]);
			if(incomingMsg.size()>1 && incomingMsg.get(1).equals("StartLeaderElection")){
				incomingMsg.clear();
				incomingMsg.add(0, "StartLeaderElection");
			}
			else {
				incomingMsg.clear();
			}

			leader = false;
			participant = false;
			sendMsg(m);
		}
	}

	// Send message to network simulator
	public void sendMsg(String m) {
		network.addMessage(getNodeId(), m);
	}

	@Override
	public void run(){

		while (true) {

			// Notify simulator
			semaphore4.release();
			try{
				if(terminate_interrupt) {
					return;
				}
				// Wait for simulator to process messages
				semaphore1.acquire();
			}
			catch (InterruptedException e) {
				return;
			}

			if (terminate) {
				return;
			}

			// Flag to start election
			if (startElection) {
				int index = 0;
				if (!incomingMsg.isEmpty() && incomingMsg.get(0).split(" ")[0].equals("STOP")) index = 1;
				incomingMsg.add(index, "StartLeaderElection");
				startElection = false;
			}

			// Process incoming message
			if (!incomingMsg.isEmpty()) {
				String[] msg;
				while (!incomingMsg.isEmpty()) {
					msg = incomingMsg.get(0).split(" ");
					if (!msg[0].equals("STOP")) {
						receiveMsg(incomingMsg.get(0));
						incomingMsg.remove(0);
						break;
					}
					else if (!broadcast_ids.contains(msg[1])){
						receiveMsg(incomingMsg.get(0));
						break;
					}
					incomingMsg.remove(0);
				}
			}

			// Notify simulator
			semaphore2.release();
			try{
				// Wait for other node threads
				semaphore3.acquire();
			}
			catch (InterruptedException e){
				// Thread failure before acquiring
				terminate_interrupt = true;
				try{
					semaphore3.acquire();
				}
				catch (InterruptedException e1){
					e1.printStackTrace();
				}
			}
		}
	}
}
