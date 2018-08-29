package com.aos;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class Core {

	public static int nodeId;
	public static HashMap<Integer, ArrayList<String>> location;
	public static ArrayList<Integer> neighbors;
	public static int node_count;
	public static int failure_count;
	public static int maxNumber;
	public static int maxPerActive;
	public static int minSendDelay;
	public static HashMap<Integer, ArrayList<Integer>> failure_events;


	public static HashMap<Integer, Connection_details> client_connections;
	public static Semaphore sem_for_client_connections = new Semaphore(1,true);

	static public final int APP_MSG_TYPE = 0;
	static public final int START_RECOVERY_MSG_TYPE = 1;
	static public final int NUMBER_OF_SEND_MSG_TYPE = 2;
	static public final int DECISION_MADE_MSG_TYPE = 3;
	static public final int LAST_RECV_ID_MSG_TYPE = 4;
	static public final int RELEASE_MSG_TYPE = 5;

	static public final int PASSIVE_STATE = 0;
	static public final int ACTIVE_STATE = 1;
	static public final int RECOVERY_STATE = 2;

	// shared between server threads have to add semaphores
	static public int state = ACTIVE_STATE; // have to add semaphore for this 0 - passive,1 - active,2 - recovery
	public static Semaphore sem_for_state = new Semaphore(1,true);

	static public int number_of_rounds_in_recovery;
	public static Semaphore sem_for_number_of_rounds_in_recovery = new Semaphore(1,true);

	public static int no_of_nodes_count_for_recovery_initiator;
	public static Semaphore sem_for_no_of_nodes_count_for_recovery_initiator = new Semaphore(1,true);

	public static boolean rolled_back_flag_for_recovery_intitator = false;
	public static Semaphore sem_for_rolled_back_flag_for_recovery_intitator = new Semaphore(1,true);

	public static int[] send_vector;

	public static int[] recv_vector;
	public static Semaphore sem_for_recv_vector = new Semaphore(1,true);

	public static int[] last_send_id;

	public static int[] last_recv_id;
	public static Semaphore sem_for_last_recv_id = new Semaphore(1,true);

	public static int[] vector_clock;
	public static Semaphore sem_for_vector_clock = new Semaphore(1,true);

	public static int total_app_messages_sent = 0;

	public static ArrayList<Checkpoint> checkpoints;
	public static Semaphore sem_for_checkpoints = new Semaphore(1,true);

	public static HashMap<Integer, ArrayList<Message>> sent_message_log_for_lost_msg;
	public static Semaphore sem_for_sent_message_log_for_lost_msg = new Semaphore(1,true);

	public static int no_of_neighbors_count_for_recovery;
	public static Semaphore sem_for_no_of_neighbors_count_for_recovery = new Semaphore(1,true);

	public static boolean flag_to_track_first_message_for_recovery;
	public static Semaphore sem_for_flag_to_track_first_message_for_recovery = new Semaphore(1,true);

	public static int parent_for_recovery;
	public static Semaphore sem_for_parent_for_recovery = new Semaphore(1,true);

	public static ArrayList<Integer> neighbors_to_send_app_msg;
	public static Semaphore sem_for_neighbors_to_send_app_msg = new Semaphore(1,true);	

	public static int recoveries_completed = 0;
	public static Semaphore sem_for_recoveries_completed = new Semaphore(1,true);

	public static int checkpoint_counter = 0;
	public static Semaphore sem_for_checkpoint_counter = new Semaphore(1,true);

	public static Semaphore sem_for_failure;

	public static boolean sent_last_recvId_msg = false;
	public static Semaphore sem_for_sent_last_recvId_msg = new Semaphore(1,true);

	public static boolean released = false;
	public static Semaphore sem_released = new Semaphore(1,true);

	public static int rolled_back_checkpoint_index;

	public static boolean flag_to_avoid_sending_duplicate_release_and_lost_msg = false;
	public static Semaphore sem_for_flag_to_avoid_sending_duplicate_release_and_lost_msg = new Semaphore(1,true);

	public static ArrayList<Message> lost_messages;
	public static Semaphore sem_for_lost_messages = new Semaphore(1,true);

	//public static boolean failed = false;
	//public static Semaphore sem_for_failed = new Semaphore(1,true);

	public static void main(String[] args) {
		nodeId = Integer.parseInt(args[0]);
		location = getLocation(args[1]);
		neighbors = getNeighbors(args[2]);
		node_count = Integer.parseInt(args[3]);
		failure_count = Integer.parseInt(args[4]);
		maxNumber = Integer.parseInt(args[5]);
		maxPerActive = Integer.parseInt(args[6]);		
		minSendDelay = Integer.parseInt(args[7]);
		failure_events = getFailure_events(args[8]);

		Core.send_vector = new int[node_count];
		Core.recv_vector = new int[node_count];
		Core.last_send_id = new int[node_count];
		Core.last_recv_id = new int[node_count];
		Core.vector_clock = new int[node_count];
		Core.checkpoints = new ArrayList<Checkpoint>(); 


		sem_for_failure = new Semaphore(0,true);

		Core.no_of_neighbors_count_for_recovery = neighbors.size();
		Core.flag_to_track_first_message_for_recovery = false;
		Core.parent_for_recovery = -1;
		Core.no_of_nodes_count_for_recovery_initiator = node_count - 1;
		Core.number_of_rounds_in_recovery = node_count;
		Core.sent_message_log_for_lost_msg = new HashMap<Integer, ArrayList<Message>>();
		lost_messages = new ArrayList<Message>();
		for(int i=0;i<Core.neighbors.size();i++) {
			Core.sent_message_log_for_lost_msg.put(Core.neighbors.get(i), new ArrayList<Message>());
		}

		//System.out.println("NodeId: "+nodeId);
		//System.out.println("location: "+location);
		//System.out.println("neighbors: "+neighbors);
		//System.out.println("node_count: "+node_count);
		//System.out.println("failure_count: "+failure_count);
		//System.out.println("maxNumber: "+maxNumber);
		//System.out.println("maxPerActive: "+maxPerActive);		
		//System.out.println("minSendDelay: "+minSendDelay);
		//System.out.println("failure_events: "+failure_events);		
		client_connections = new HashMap<Integer, Connection_details>();
		neighbors_to_send_app_msg = target_recipients();	
		Core.save_checkpoint(neighbors_to_send_app_msg);
		Core.state = Core.ACTIVE_STATE;
		Thread get_connections = new Thread(new Get_connections());
		get_connections.start();

		try {
			Thread.sleep(5000);
			//			Iterator<Integer> itr = Core.neighbors.iterator();
			//			while(itr.hasNext()) {
			//				int Nodeid = itr.next();
			Socket clientSocket = null;
			for(int i=0;i<Core.neighbors.size();i++) {
				String NodeIp = Core.location.get(Core.neighbors.get(i)).get(0);
				int NodePort = Integer.parseInt(Core.location.get(Core.neighbors.get(i)).get(1));
				//System.out.println("Connecting to node "+NodeIp+" port "+NodePort);				
				clientSocket = new Socket(NodeIp,NodePort);
				ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
				Core.client_connections.put(Core.neighbors.get(i), new Connection_details(clientSocket, outputStream, null));
				//System.out.println(client_connections);
			}
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}




		Thread application = new Thread(new Application());
		application.start();

		/*		
		for(int i=0;i<failure_count;i++) {
//			while(checkpoint_counter < failure_events.get(i).get(1)) { // update on checkpoint
//				
//			}
			try {
				sem_for_failure.acquire();
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}

			if (nodeId==failure_events.get(i).get(0)){
				try {
					sem_for_state.acquire();
				} catch (InterruptedException e) {
					sem_for_state.release();
					e.printStackTrace();
				}
				//System.out.println("Detected failure sending start recovery "+failure_events.get(i).get(0)+", "+failure_events.get(i).get(1)+"counter "+checkpoint_counter+"checkpoint size"+checkpoints.size());
				state = RECOVERY_STATE;
				rollback_for_failure();				// roll back to a random checkpoint
				sem_for_state.release();

				// broadcast start recovery message to neighbors
				Message start_recovery_msg = new Message();
				start_recovery_msg.sourceId = nodeId;
				start_recovery_msg.MessageType = START_RECOVERY_MSG_TYPE;
				start_recovery_msg.recovery_initiator_id = nodeId;
				try {
					sem_for_client_connections.acquire();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				for (int j=0;j<neighbors.size();j++){ 
					try {

						Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(start_recovery_msg);
						//System.out.println("sent start recovery to "+Core.neighbors.get(j));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				sem_for_client_connections.release();
				//System.out.println("sent start recovery to all the neighbors");
			}
			try {
				sem_for_failure.acquire();
				//System.out.println("Acquired lock on sem_for_failure");
				checkpoint_counter = 0;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		 */
		//System.out.println("Ending Main");
	}


	public static void handle_failure_event() {
		//System.out.println("Detected failure sending start recovery counter "+checkpoint_counter+" , checkpoint size "+checkpoints.size());
		state = RECOVERY_STATE;
		rollback_for_failure();
		Message start_recovery_msg = new Message();
		start_recovery_msg.sourceId = nodeId;
		start_recovery_msg.MessageType = START_RECOVERY_MSG_TYPE;
		start_recovery_msg.recovery_initiator_id = nodeId;
		try {
			sem_for_client_connections.acquire();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for (int j=0;j<neighbors.size();j++){ 
			try {

				Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(start_recovery_msg);
				//System.out.println("sent start recovery to "+Core.neighbors.get(j));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		sem_for_client_connections.release();
		//System.out.println("sent start recovery to all the neighbors");
	}

	public static int[] convert_string_to_arr(String str,int arr_size) {
		String[] str_arr = str.split(" ");
		int[] arr = new int[arr_size];
		for(int i=0;i<str_arr.length;i++) {
			arr[i] = Integer.parseInt(str_arr[i]);
		}
		return arr;
	}


	public static ArrayList<Integer> convert_string_to_arrlist(String str) {
		String[] str_arr = str.split(" ");
		ArrayList<Integer> arr = new ArrayList<Integer>();
		for(int i=0;i<str_arr.length;i++) {
			try {
				arr.add(Integer.parseInt(str_arr[i]));
			} catch(NumberFormatException e) {

			}
		}
		return arr;
	}

	public static void rollback_for_failure() {
		int index_to_roll_to = 1;		

		if (checkpoints.size()>=6){
			Random rand = new Random();
			index_to_roll_to = checkpoints.size() - (rand.nextInt(5)+1);
		}

		//System.out.println("Index to roll back "+index_to_roll_to);
		//System.out.println("size "+Core.checkpoints.size());
		//System.out.println("send_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).send_vector));
		//System.out.println("recv_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).recv_vector));
		//System.out.println("last_send_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).vector_clock));
		//System.out.println("total_app_messages_sent "+Core.checkpoints.get(Core.checkpoints.size()-1).messages_sent);
		//System.out.println("state "+Core.checkpoints.get(Core.checkpoints.size()-1).state);
		//System.out.println("neighbors_to_send "+Core.checkpoints.get(Core.checkpoints.size()-1).nodeid_of_messages_to_send);

		checkpoints.subList(index_to_roll_to, checkpoints.size()).clear();
		Checkpoint new_checkpoint = checkpoints.get(checkpoints.size()-1);
		vector_clock = new_checkpoint.vector_clock;
		send_vector = new_checkpoint.send_vector;
		recv_vector = new_checkpoint.recv_vector;
		last_recv_id = new_checkpoint.last_recv_id;
		last_send_id = new_checkpoint.last_send_id;
		total_app_messages_sent = new_checkpoint.messages_sent;
		neighbors_to_send_app_msg = new_checkpoint.nodeid_of_messages_to_send;
		//System.out.println("size "+Core.checkpoints.size());
		//System.out.println("send_vector "+convert_to_string(send_vector));
		//System.out.println("recv_vector "+convert_to_string(recv_vector));
		//System.out.println("last_send_id "+convert_to_string(last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(recv_vector));
		//System.out.println("###############################");

		vector_clock = convert_string_to_arr(new_checkpoint.str_vector_clock,Core.node_count);
		send_vector = convert_string_to_arr(new_checkpoint.str_send_vector,Core.node_count);
		recv_vector = convert_string_to_arr(new_checkpoint.str_recv_vector,Core.node_count);
		last_recv_id = convert_string_to_arr(new_checkpoint.str_last_recv_id,Core.node_count);
		last_send_id = convert_string_to_arr(new_checkpoint.str_last_send_id,Core.node_count);
		total_app_messages_sent = new_checkpoint.messages_sent;
		neighbors_to_send_app_msg = convert_string_to_arrlist(new_checkpoint.str_nodeid_of_messages_to_send);

		//System.out.println("send_vector "+convert_to_string(send_vector));
		//System.out.println("recv_vector "+convert_to_string(recv_vector));
		//System.out.println("last_send_id "+convert_to_string(last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(recv_vector));


		//System.out.println("str_send_vector "+new_checkpoint.str_send_vector);
		//System.out.println("str_recv_vector "+new_checkpoint.str_recv_vector);
		//System.out.println("str_last_send_id "+new_checkpoint.str_last_send_id);
		//System.out.println("str_last_recv_id "+new_checkpoint.str_last_recv_id);
		//System.out.println("str_vector_clock "+new_checkpoint.str_vector_clock);
		//System.out.println("total_app_messages_sent "+total_app_messages_sent);
		//System.out.println("state "+new_checkpoint.state);
		//System.out.println("neighbors_to_send "+neighbors_to_send_app_msg);



		//System.out.println("************************");

		//System.out.println("size "+Core.checkpoints.size());
		//System.out.println("send_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).send_vector));
		//System.out.println("recv_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).recv_vector));
		//System.out.println("last_send_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).vector_clock));


		//System.out.println("str_send_vector "+Core.checkpoints.get(Core.checkpoints.size()-1).str_send_vector);
		//System.out.println("str_recv_vector "+Core.checkpoints.get(Core.checkpoints.size()-1).str_recv_vector);
		//System.out.println("str_last_send_id "+Core.checkpoints.get(Core.checkpoints.size()-1).str_last_send_id);
		//System.out.println("str_last_recv_id "+Core.checkpoints.get(Core.checkpoints.size()-1).str_last_recv_id);
		//System.out.println("str_vector_clock "+Core.checkpoints.get(Core.checkpoints.size()-1).str_vector_clock);
		//System.out.println("total_app_messages_sent "+Core.checkpoints.get(Core.checkpoints.size()-1).messages_sent);

		//System.out.println("state "+Core.checkpoints.get(Core.checkpoints.size()-1).state);
		//System.out.println("neighbors_to_send "+Core.checkpoints.get(Core.checkpoints.size()-1).nodeid_of_messages_to_send);

		for(Iterator<Map.Entry<Integer,ArrayList<Message>>> it=sent_message_log_for_lost_msg.entrySet().iterator();it.hasNext();){
			Map.Entry<Integer, ArrayList<Message>> entry = it.next();
			int node_id = entry.getKey();
			ArrayList<Message> msg = entry.getValue();
			int last_sent_msg_id = last_send_id[node_id];
			msg.subList(last_sent_msg_id, msg.size()).clear();
		}	

	}	



	public static void rollback_checkpoint(int index) {
		try {
			sem_for_checkpoints.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		checkpoints.subList(index, checkpoints.size()).clear();



		Checkpoint new_checkpoint = checkpoints.get(checkpoints.size()-1);
		vector_clock = convert_string_to_arr(new_checkpoint.str_vector_clock,Core.node_count);
		send_vector = convert_string_to_arr(new_checkpoint.str_send_vector,Core.node_count);
		recv_vector = convert_string_to_arr(new_checkpoint.str_recv_vector,Core.node_count);
		last_recv_id = convert_string_to_arr(new_checkpoint.str_last_recv_id,Core.node_count);
		last_send_id = convert_string_to_arr(new_checkpoint.str_last_send_id,Core.node_count);
		total_app_messages_sent = new_checkpoint.messages_sent;
		neighbors_to_send_app_msg = convert_string_to_arrlist(new_checkpoint.str_nodeid_of_messages_to_send);		


		//System.out.println("size "+Core.checkpoints.size());
		//System.out.println("send_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).send_vector));
		//System.out.println("recv_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).recv_vector));
		//System.out.println("last_send_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).vector_clock));
		//System.out.println("total_app_messages_sent "+Core.checkpoints.get(Core.checkpoints.size()-1).messages_sent);
		//System.out.println("state "+Core.checkpoints.get(Core.checkpoints.size()-1).state);
		//System.out.println("neighbors_to_send "+Core.checkpoints.get(Core.checkpoints.size()-1).nodeid_of_messages_to_send);
		// have to change
		for(Iterator<Map.Entry<Integer,ArrayList<Message>>> it=sent_message_log_for_lost_msg.entrySet().iterator();it.hasNext();){
			Map.Entry<Integer, ArrayList<Message>> entry = it.next();
			int node_id = entry.getKey();
			ArrayList<Message> msg = entry.getValue();
			int last_sent_msg_id = last_send_id[node_id];
			msg.subList(last_sent_msg_id, msg.size()).clear();
		}	

		sem_for_checkpoints.release();
	}

	public static ArrayList<Integer> target_recipients() {
		ArrayList<Integer> neighbors_to_send;
		if(Core.neighbors.size() > Core.maxPerActive) {
			Collections.shuffle(Core.neighbors);
			neighbors_to_send = new ArrayList<Integer>(Core.neighbors.subList(0,Core.maxPerActive));			
		} else {
			neighbors_to_send = new ArrayList<Integer>(Core.neighbors);
		}
		return neighbors_to_send;
	}

	public static HashMap<Integer, ArrayList<Integer>> getFailure_events(String str) {
		if(!str.trim().equals("")) {
			String a[] = str.split("#");
			HashMap<Integer, ArrayList<Integer>> map = new HashMap<Integer, ArrayList<Integer>>();
			for(int i=0;i<a.length;i++) {
				//System.out.print(a[i]+",");
				String b[] = a[i].split(" ");
				ArrayList<Integer> arr = new ArrayList<Integer>();
				arr.add(Integer.parseInt(b[0].trim()));
				arr.add(Integer.parseInt(b[1].trim()));
				map.put(i,arr);
			}
			return map;

		}
		HashMap<Integer, ArrayList<Integer>> map = new HashMap<Integer, ArrayList<Integer>>();
		return map;
	}


	public static HashMap<Integer, ArrayList<String>> getLocation(String str) {
		HashMap<Integer, ArrayList<String>> map = null;
		if(!str.trim().equals("")) {
			String a[] = str.split("#");
			map = new HashMap<Integer, ArrayList<String>>();
			for(int i=0;i<a.length;i++) {
				//System.out.print(a[i]+",");
				String b[] = a[i].split(" ");
				ArrayList<String> arr = new ArrayList<String>();
				arr.add(b[1].trim());
				arr.add(b[2].trim());
				map.put(Integer.parseInt(b[0]),arr);
			}
		}
		return map;
	}
	public static ArrayList<Integer> getNeighbors(String str) {
		ArrayList<Integer> arr = null;
		if(!str.trim().equals("")) {
			String a[] = str.split(" ");
			arr = new ArrayList<Integer>();
			for(int i=0;i<a.length;i++) {
				arr.add(Integer.parseInt(a[i]));
			}
		}
		return arr;
	}

	public static void array_copy(int[] new_arr,int[] old_arr) {
		for(int i=0;i<old_arr.length;i++) {
			new_arr[i] = old_arr[i];
		}
	}

	public static ArrayList<Integer> arraylist_copy(ArrayList<Integer> neighbors_to_send_old) {
		ArrayList<Integer> new_copy_of_neighbors_to_send = new ArrayList<Integer>();
		for(int i=0;i<neighbors_to_send_old.size();i++ ) {
			new_copy_of_neighbors_to_send.add(neighbors_to_send_old.get(i));
		}
		return new_copy_of_neighbors_to_send;
	}

	public static boolean save_checkpoint(ArrayList<Integer> neighbors_to_send) {
		// add semaphores if required
		int[] new_send_vector = new int[node_count];
		int[] new_recv_vector = new int[node_count];
		int[] new_last_send_id = new int[node_count];
		int[] new_last_recv_id = new int[node_count];
		int[] new_vector_clock = new int[node_count];
		String str_neighbors_to_send;
		/*
		System.arraycopy( send_vector, 0, new_send_vector, 0, send_vector.length );
		System.arraycopy( recv_vector, 0, new_recv_vector, 0, recv_vector.length );
		System.arraycopy( last_send_id, 0, new_last_send_id, 0, last_send_id.length );
		System.arraycopy( last_recv_id, 0, new_last_recv_id, 0, last_recv_id.length );
		System.arraycopy( vector_clock, 0, new_vector_clock, 0, vector_clock.length );
		 */
		array_copy(new_send_vector, send_vector);
		array_copy(new_recv_vector, recv_vector);
		array_copy(new_last_send_id, last_send_id);
		array_copy(new_last_recv_id, last_recv_id);
		array_copy(new_vector_clock, vector_clock);
		str_neighbors_to_send = arraylist_to_string(neighbors_to_send);

		Core.checkpoints.add(new Checkpoint(new_send_vector, new_recv_vector, new_last_send_id, new_last_recv_id, new_vector_clock,
				convert_to_string(new_send_vector),convert_to_string(new_recv_vector),convert_to_string(new_last_send_id),convert_to_string(new_last_recv_id),convert_to_string(new_vector_clock),
				total_app_messages_sent, Core.state,neighbors_to_send,str_neighbors_to_send));
		//System.out.println("size "+Core.checkpoints.size());
		//System.out.println("send_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).send_vector));
		//System.out.println("recv_vector "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).recv_vector));
		//System.out.println("last_send_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_send_id));
		//System.out.println("last_recv_id "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).last_recv_id));
		//System.out.println("vector_clock "+convert_to_string(Core.checkpoints.get(Core.checkpoints.size()-1).vector_clock));

		//System.out.println("str_send_vector "+Core.checkpoints.get(Core.checkpoints.size()-1).str_send_vector);
		//System.out.println("str_recv_vector "+Core.checkpoints.get(Core.checkpoints.size()-1).str_recv_vector);
		//System.out.println("str_last_send_id "+Core.checkpoints.get(Core.checkpoints.size()-1).str_last_send_id);
		//System.out.println("str_last_recv_id "+Core.checkpoints.get(Core.checkpoints.size()-1).str_last_recv_id);
		//System.out.println("str_vector_clock "+Core.checkpoints.get(Core.checkpoints.size()-1).str_vector_clock);

		//System.out.println("total_app_messages_sent "+Core.checkpoints.get(Core.checkpoints.size()-1).messages_sent);
		//System.out.println("state "+Core.checkpoints.get(Core.checkpoints.size()-1).state);
		checkpoint_counter++;

		if ((Core.nodeId==Core.failure_events.get(Core.recoveries_completed).get(0)) && (checkpoint_counter >= failure_events.get(recoveries_completed).get(1))) {
			//sem_for_failure.release();
			handle_failure_event();
			return false;
		}
		return true;
	}

	public static String arraylist_to_string(ArrayList<Integer> arr) {
		StringBuilder arr_str = new StringBuilder();
		for(int i=0;i<arr.size();i++) {
			arr_str.append(arr.get(i)+" ");
		}
		return arr_str.toString().trim();
	}

	public static String convert_to_string(int[] arr) {
		StringBuilder arr_str = new StringBuilder();
		for(int i=0;i<arr.length;i++) {
			arr_str.append(arr[i]+" ");
		}
		return arr_str.toString().trim();
	}

	public static void write_vector_clock_to_file() {
		String fileName = nodeId+".out";
		File file = new File(fileName);		
		Writer fileWriter = null;
		BufferedWriter bufferedWriter = null;
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			fileWriter = new FileWriter(file,true);
			bufferedWriter = new BufferedWriter(fileWriter);
			// Write the lines one by one
			StringBuilder vector_string = new StringBuilder();
			for(int i=0;i<vector_clock.length;i++) {
				vector_string.append(vector_clock[i]+" ");
			}			
			bufferedWriter.write(vector_string.toString().trim()+"\n");
		} catch (IOException e) {
			System.err.println("Error writing the file : "+fileName);
			e.printStackTrace();
		} finally {
			if (bufferedWriter != null && fileWriter != null) {
				try {
					bufferedWriter.close();
					fileWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}






}
