import java.util.*;
import java.io.*;
import java.util.concurrent.Semaphore;

/* 
Class to simulate the network. System design directions:

- Synchronous communication: each round lasts for 20ms
- At each round the network receives the messages that the nodes want to send and delivers them
- The network should make sure that:
	- A node can only send messages to its neighbours
	- A node can only send one message per neighbour per round
- When a node fails, the network must inform all the node's neighbours about the failure
*/

public class Network {

	private static List<Node> nodes;
	private int round;
	private int period = 20;
	private Map<Integer, String> msgToDeliver; //Integer for the id of the sender and String for the message

	// Semaphores for barrier emulation
	public Semaphore semaphore1;
	public Semaphore semaphore2;
	public Semaphore semaphore3;
	public Semaphore semaphore4;


	private Boolean leaderFound = false;
	private String leader = "";
	private FileWriter writer;
	private int broadcast_id = 0;
	private Map<Integer, Integer> id_visited;
	private boolean interrupted = false;
	private boolean fail = false;

	private ArrayList<Node> ring;
	private boolean ring_found = false;

	public void NetSimulator(String graphName, String leaderElection) {
		msgToDeliver = new HashMap<>();
		nodes = new ArrayList<>();
		parseGraph(graphName);
		List<String> instructions = parseLeaderElection(leaderElection);

		semaphore1 = new Semaphore(0);
		semaphore2 = new Semaphore(0);
		semaphore3 = new Semaphore(0);
		semaphore4 = new Semaphore(0);

		try{
			writer = new FileWriter("log.txt");
		}
		catch (Exception e){
			e.printStackTrace();
		}

		boolean complete = false;
		round = 0;

		for (Node n : nodes){
			n.semaphore1 = semaphore1;
			n.semaphore2 = semaphore2;
			n.semaphore3 = semaphore3;
			n.semaphore4 = semaphore4;
			n.start();
		}

		while (!complete){

			// Read  next instruction
			if (!instructions.isEmpty()){
				String instruction = instructions.get(0);
				String[] input = instruction.split(" ", 0);
				if(Integer.parseInt(input[1]) == round && input[0].equals("ELECT")){
					for (int i = 2; i < input.length; i++){
						for (Node n : nodes){
							if (n.getNodeId() == Integer.parseInt(input[i])){
								n.startElection = true;
							}
						}
					}
					instructions.remove(0);
				}
				if(Integer.parseInt(input[1]) == round && input[0].equals("FAIL")){
					fail = true;
					msgToDeliver.clear();
					for (int i = 2; i < input.length; i++){
						System.out.println("Round " + round + ": Node " + Integer.parseInt(input[i]) + " failure");

						// Notify nodes of failure
						broadcastFailure(Integer.parseInt(input[i]));

						// Adjust the neighbours of nodes
						adjustNeighbours(Integer.parseInt(input[i]));

						// Terminate the failed node thread
						deleteFailedNode(Integer.parseInt(input[i]));

						// Create a new ring
						recreateRing();

					}
					instructions.remove(0);
				}
			}

			deliverMessages();

			// Leader was found in previous round, print out if a new leader was elected.
			if(leaderFound){

				try{
					writer.write(leader + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println("Round " + round + ": Leader elected -> " + leader);


				leaderFound = false;
			}

			// Notify threads that messages have been distributed
			try{
				if (interrupted){
					semaphore4.acquire(nodes.size()+1);
					interrupted = false;
				}
				else{
					semaphore4.acquire(nodes.size());
				}

				semaphore1.release(nodes.size());
			}
			catch (Exception e){
				e.printStackTrace();
			}

			// Wait for threads to handle the messages
			try{
				semaphore2.acquire(nodes.size());
				semaphore3.release(nodes.size());
			}
			catch (Exception e){
				e.printStackTrace();
			}

			if(msgToDeliver.isEmpty() && instructions.isEmpty()){
				// Termination condition
				complete = true;
				try{
					for(Node n : nodes){
						n.terminate = true;
					}

					semaphore1.release(nodes.size());

					for(Node n : nodes){
						n.join();
					}

				}
				catch (Exception e){
					e.printStackTrace();
				}
			}

			try{
				Thread.sleep(period);
			}
			catch (Exception e){
				e.printStackTrace();
			}

			round++;
		}
		try{
			if (fail){
				writer.write("simulation completed");
			}
			writer.close();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	public void parseGraph(String graphName){

		// Populate nodes and set neighbours
		try {
			FileReader graph = new FileReader(getClass().getResource(graphName).getPath());
			BufferedReader br = new BufferedReader(graph);
			String line = br.readLine();
			while (line != null) {
				String[] nodeData = line.split(" ", 0);
				nodes.add(new Node(Integer.parseInt(nodeData[0]), this));
				line = br.readLine();
			}

			graph = new FileReader(getClass().getResource(graphName).getPath());
			br = new BufferedReader(graph);
			line = br.readLine();

			int counter = 0;

			// Setup neighbours
			while (line != null) {
				String[] nodeData = line.split(" ", 0);
				// Parse neighbours from graph
				for (int i = 1; i < nodeData.length; i++){
					for (Node node : nodes){
						if (node.getNodeId() == Integer.parseInt(nodeData[i])){
							nodes.get(counter).addNeighbour(node);
						}
					}
				}

				// Construct initial ring by setting previous and next line as neighbour
				if (nodes.size() > 1){
					if (counter == 0){
						nodes.get(counter).addNeighbour(nodes.get(counter+1));
						nodes.get(counter).setNext(nodes.get(counter+1));
						nodes.get(counter).addNeighbour(nodes.get(nodes.size()-1));
					}
					else if(counter == nodes.size()-1){
						nodes.get(counter).addNeighbour(nodes.get(0));
						nodes.get(counter).setNext(nodes.get(0));
						nodes.get(counter).addNeighbour(nodes.get(counter-1));
					}
					else{
						nodes.get(counter).addNeighbour(nodes.get(counter-1));
						nodes.get(counter).addNeighbour(nodes.get(counter+1));
						nodes.get(counter).setNext(nodes.get(counter+1));
					}
				}

				counter++;
				line = br.readLine();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	public List<String> parseLeaderElection(String fileName){
		// Parse and store the instructions fed to program
		List<String> instructions = new ArrayList<>();
		try{
			FileReader graph = new FileReader(getClass().getResource(fileName).getPath());
			BufferedReader br = new BufferedReader(graph);
			String line = br.readLine();

			while (line != null) {
				instructions.add(line);
				line = br.readLine();
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return instructions;
	}

	public void deleteFailedNode(int id){
		// removes failed node and starts election at its next neighbour

		Iterator<Node> it =  nodes.iterator();
		while (it.hasNext()) {
			Node node = it.next();
			if (node.getNodeId() == id) {
				node.next.startElection = true;
				node.interrupt();
				interrupted = true;
				it.remove();
				try {
					node.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	public void adjustNeighbours(int id){
		// Adjusts neighbours by removing the failed node from neighbour lists
		for(Node node : nodes){
			if (node.getNodeId() != id){
				node.getNeighbors().removeIf(n -> n.getNodeId() == id);
			}
		}
	}

	public void recreateRing(){
		ring = new ArrayList<>();
		id_visited = new HashMap<>();

		for(Node node : nodes){
			id_visited.put(node.getNodeId(), 0);
		}

		dfs(nodes.get(0).getNodeId(), nodes.get(0), 1);
		if(ring_found){
			System.out.print("Round " + round + ": New ring after failure: ");
			for (Node node : ring){
				System.out.print(node.getNodeId() + " ");
			}
			System.out.println();
			// Setup neighbours
			for(int i = 0; i < ring.size(); i++){
				if(i == ring.size()-1){
					ring.get(i).setNext(ring.get(0));
				}
				else{
					ring.get(i).setNext(ring.get(i+1));
				}
			}
			ring_found = false;
		}
		else{
			// No ring was found, terminate.
			try{
				writer.write("simulation completed\n");
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println("Round " + round + ": Terminating as no ring was found in network.");
			System.exit(1);
		}

	}

	public void dfs(int id, Node node, int depth){
		// Create new ring
		id_visited.put(node.getNodeId(), 1);

		for (Node neigh : node.getNeighbors()){
			if(id_visited.get(neigh.getNodeId()) == 0){
				// Node not visited yet, go deeper
				dfs(id, neigh, depth+1);
			}
			else if(neigh.getNodeId() == id){
				if (depth == nodes.size()){
					// Ring was found, recurse up the graph.
					ring.add(0, node);
					ring_found = true;
					return;
				}
			}
			if (ring_found){
				// Add nodes to ring while recursing back
				ring.add(0, node);
				return;
			}
		}

		id_visited.put(id,0);
	}

	public synchronized void addMessage(int id, String m) {
		// Add message to a list of messages to be delivered
		try{
			if (!msgToDeliver.containsKey(id)){
				//writer.write("Node " + id + ": " + m + "\n");
				msgToDeliver.put(id,m);
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}

	}
	
	public synchronized void deliverMessages() {
		for (Node node : nodes) {
			if (msgToDeliver.containsKey(node.getNodeId())) {

				String m = msgToDeliver.get(node.getNodeId());
				String[] mSplit = m.split(" ");

				// If broadcast, send to all neighbours
				if (mSplit[0].equals("STOP")){
					System.out.print("Round " + round + ": Node " + node.getNodeId() + " -> msg: " + m + ", recipients: ");
					for (Node n : node.getNeighbors()){
						System.out.print(n.getNodeId() + " ");
						n.incomingMsg.add(0, m);
					}
					System.out.println();
				}
				else{
					// Send message to neighbour node
					node.getNext().incomingMsg.add(m);
					System.out.println("Round " + round + ": Node " + node.getNodeId() + " -> msg: " + m + ", recipients: " + node.getNext().getNodeId());
				}

				if (msgToDeliver.get(node.getNodeId()).equals("LEADER " + node.getNodeId())) {
					// Leader was found
					leaderFound = true;
					leader = "Leader Node " + node.getNodeId();
				}
				msgToDeliver.remove(node.getNodeId());
			}
		}
		msgToDeliver.clear();
	}

	public void broadcastFailure(int id) {

		// Broadcast the failure, starting with the neighbours of the failed node
		System.out.print("Round " + round + ": Broadcasting failure to neighbours ");
		for (Node node : nodes) {
			if (node.getNodeId() == id) {
				for (Node n : node.getNeighbors()) {
					System.out.print(n.getNodeId() + " ");
					n.incomingMsg.add(0, "STOP " + broadcast_id);
				}
				broadcast_id++;
			}
		}
		System.out.println();
	}
	
	
	public static void main(String[] args) {
		Network network = new Network();
		network.NetSimulator(args[0], args[1]);

	}
	
	
	
	
	
	
	
}
