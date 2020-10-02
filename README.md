# leader-election-simulation

Java leader election simulation within a network making use of the ring-based Chang and Roberts algorithm. Project was created for an distributed systems university assignment.

## Usage

 Run run.sh with graph and event inputs (see ds_graph.txt and ds_elect.txt/ds_fail.txt). 
 
 
# Design decisions and implementation explanation

## File reading and initial ring creation
I make use of the java BufferedReader to parse the files. The election instructions are stored in an array, while the original node sequence is used to create the initial graph. During parsing node neighbour lists are populated, as well as a next variable set, indicating the next neighbour in the ring.

## Leader election start
When a node is supposed to start the election, the network simulator explicitly sets a flag for the node to start an election. That is interpreted by the node by setting a special message at the front of its message buffer, causing it to prioritise starting the election on that round.

## Message delivery
All nodes use the network simulators addMessage() function to add a message to be delivered in a round. Each node only sends 1 message per round, which is enforced by the simulator. The simulator then delivers the messages to the next node in the ring from the originating node, or all neighbours in case of a broadcast message.

## Message parsing
All nodes process the first message in their buffer on every round. They can receive 4 different messages - Elect, Forward, Leader and STOP. The first tree are handled as described by the algorithm. STOP indicates a broadcast message which is forwarded to all neighbours of a failing node.

## Synchronization
A barrier synchronization model was used. Due to Part B including failing nodes, I chose to not use CyclicBarriers, as the amount of threads to wait for cannot be changed after the barrier initialization. Thus 4 semaphores are used to mimic the barrier behaviour. They act as barriers where nodes have to wait for the network simulator to distribute messages and are only then allowed to process them. The network simulator then waits until all nodes are done delivering their messages, starting only after they're done.

## Node Failure
Upon node failure I assume that the network is informed of the node failure within the same round of it failing. The network also knows the neighbours of the node and can thus act according to the following protocol: Upon a node failure 1. Adjust the neighbours of each node. 2. Terminate the failed node (simulation purposes). 3. Recreate the ring using a depth-first-search approach. 4. notify the failing node's neighbours through a broadcast message and tell the next node of the failing node to start an election in the next round.


## Broadcast Message
The broadcast message is sent with the tag STOP broadcast_id, indicating that some node has failed. Upon receiving a broadcast message for the first time, the receiving node clears its incomingMsg list. The node then forwards the broadcast message to all its neighbours. The broadcasting completes when all nodes have received the broadcast with a specific ID at least once. The broadcast also starts a new leader election - when the next node in the ring of the failing node receives the broadcast it will start a leader election on the subsequent round.


## Ring recreation
A dfs approach is used to find the new ring amongst the remaining nodes. It starts with the first node in the node list and recurses using the neighbour information to form a tree, backtracking when a ring of insufficient length has been found, or no neighbours of a visited node remain. It terminates once it finds the first ring which contains all nodes.
