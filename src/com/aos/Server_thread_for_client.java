package com.aos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Semaphore;

public class Server_thread_for_client implements Runnable {
	Socket socket;
	ObjectInputStream inStream;
	public Server_thread_for_client(Connection_details conn_details) {
		this.socket = conn_details.sock;
		this.inStream = conn_details.inStream;
	}

	public ArrayList<Integer> target_recipients() {
		ArrayList<Integer> neighbors_to_send;
		if(Core.neighbors.size() > Core.maxPerActive) {
			Collections.shuffle(Core.neighbors);
			neighbors_to_send = new ArrayList<Integer>(Core.neighbors.subList(0,Core.maxPerActive));
		} else {
			neighbors_to_send = new ArrayList<Integer>(Core.neighbors);
		}
		return neighbors_to_send;
	}
	public void run() {
		while(true) { // have write terminating condition
			try {
				Core.sem_for_recoveries_completed.acquire();
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			//System.out.println("Number of recoveries done "+Core.recoveries_completed);
			if(Core.recoveries_completed == Core.failure_count) {
				Core.sem_for_recoveries_completed.release();
				break;
			}
			Core.sem_for_recoveries_completed.release();
			try {
				//System.out.println("Waiting for messages in run");
				Message recvMessage = (Message) inStream.readObject();
				switch(recvMessage.MessageType) {
				case Core.APP_MSG_TYPE:
					
					try {
						Core.sem_for_checkpoints.acquire();
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					if ((Core.nodeId==Core.failure_events.get(Core.recoveries_completed).get(0) && Core.checkpoint_counter >= Core.failure_events.get(Core.recoveries_completed).get(1))){
						Core.sem_for_checkpoints.release();
						break;
					}					
					Core.sem_for_checkpoints.release();
					
					try {
						Core.sem_for_state.acquire();
						Core.sem_for_vector_clock.acquire();
						Core.sem_for_checkpoints.acquire();
					} catch (InterruptedException e) {
						Core.sem_for_checkpoints.release();
						Core.sem_for_vector_clock.release();
						Core.sem_for_state.release();
						e.printStackTrace();
					}
					if(Core.state == Core.PASSIVE_STATE && Core.total_app_messages_sent < Core.maxNumber) {
						Core.recv_vector[recvMessage.sourceId]++;
						Core.last_recv_id[recvMessage.sourceId]++;
						//System.out.println("Got a application message from "+ recvMessage.sourceId+" with app_msg id "+recvMessage.app_message_id+" changing state from passive to active");
						//System.out.println("Vector clock "+Core.convert_to_string(recvMessage.vector_ts)+" from "+recvMessage.sourceId);
						Core.state = Core.ACTIVE_STATE;
						Core.neighbors_to_send_app_msg = target_recipients();
						int[] recv_vectorclock = recvMessage.vector_ts;
						for(int i=0;i<Core.node_count;i++) {
							Core.vector_clock[i] = Math.max(Core.vector_clock[i], recv_vectorclock[i]);
						}
						Core.vector_clock[Core.nodeId]++;
						boolean failure_event = Core.save_checkpoint(Core.neighbors_to_send_app_msg);
						if(failure_event) {
							Thread application = new Thread(new Application());
							application.start();							
						}
					} else if(Core.state == Core.ACTIVE_STATE) {
						Core.recv_vector[recvMessage.sourceId]++;
						Core.last_recv_id[recvMessage.sourceId]++;
						//System.out.println("Got a application message from "+ recvMessage.sourceId+" with app_msg id "+recvMessage.app_message_id);
						int[] recv_vectorclock = recvMessage.vector_ts;
						for(int i=0;i<Core.node_count;i++) {
							Core.vector_clock[i] = Math.max(Core.vector_clock[i], recv_vectorclock[i]);
						}
						Core.vector_clock[Core.nodeId]++;
						Core.save_checkpoint(Core.neighbors_to_send_app_msg);
					} else {
						// ignore App messages in recovery
						//System.out.println("In recovery so ignoring app message from "+recvMessage.sourceId);
					}
					Core.sem_for_checkpoints.release();
					Core.sem_for_vector_clock.release();
					Core.sem_for_state.release();
					break;
				case Core.START_RECOVERY_MSG_TYPE:
					//System.out.println("Got a start recovery changing state to recovery from "+recvMessage.sourceId);
					try {
						Core.sem_for_state.acquire();
					} catch (InterruptedException e) {
						Core.sem_for_state.release();
						e.printStackTrace();
					}
					Core.state = Core.RECOVERY_STATE;
					Core.sem_for_state.release();
					if(Core.nodeId == recvMessage.recovery_initiator_id) {
						recovery_as_initiator(recvMessage);
					} else {
						recovery(recvMessage);
					}
					break;
				}

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	//	Core.sem_for_failure.release();
		//System.out.println("Ending thread");
	}



	public void recovery_as_initiator(Message recvMessage) {
		boolean temp = true;
		boolean gotlastrcvidmsg = false;
		while(temp){

			// recv next message
			try {
				recvMessage = (Message) inStream.readObject();
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			try {
				Core.sem_for_number_of_rounds_in_recovery.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (Core.number_of_rounds_in_recovery == 0)
				temp = false;
			Core.sem_for_number_of_rounds_in_recovery.release();

			if (recvMessage.MessageType == Core.LAST_RECV_ID_MSG_TYPE) {
				//System.out.println("received last recv id message from "+recvMessage.sourceId);
				gotlastrcvidmsg = true;
			}
			if(recvMessage.MessageType == Core.DECISION_MADE_MSG_TYPE) {
				try {
					Core.sem_for_no_of_nodes_count_for_recovery_initiator.acquire();
					Core.sem_for_number_of_rounds_in_recovery.acquire();
				} catch (InterruptedException e) {
					Core.sem_for_no_of_nodes_count_for_recovery_initiator.release();
					e.printStackTrace();
				}
				Core.no_of_nodes_count_for_recovery_initiator--;
				//System.out.println("Got a decision made message for round "+Core.number_of_rounds_in_recovery+" from node "+recvMessage.sourceId+" with rollback flag "+recvMessage.rolledback+", "+Core.no_of_nodes_count_for_recovery_initiator);
				Core.rolled_back_flag_for_recovery_intitator = Core.rolled_back_flag_for_recovery_intitator | recvMessage.rolledback;
				if(Core.no_of_nodes_count_for_recovery_initiator == 0) {
					Core.no_of_nodes_count_for_recovery_initiator = Core.node_count - 1;
					Core.number_of_rounds_in_recovery--;
					if(!Core.rolled_back_flag_for_recovery_intitator || Core.number_of_rounds_in_recovery == 0) {
						Core.rolled_back_flag_for_recovery_intitator = false;
						Core.number_of_rounds_in_recovery = 0;
						temp = false;
						Core.sent_last_recvId_msg = false;
						//System.out.println("Done with sending send vector msgs");
						//	break;
					} else {
						// send send vector
						Core.rolled_back_flag_for_recovery_intitator = false;
						Message send_vector_message = new Message();
						send_vector_message.sourceId = Core.nodeId;
						send_vector_message.MessageType = Core.NUMBER_OF_SEND_MSG_TYPE;	
						//System.out.println("sending send vector to neighbors");
						try {
							Core.sem_for_client_connections.acquire();
						} catch (InterruptedException e1) {
							Core.sem_for_client_connections.release();
							e1.printStackTrace();
						}						
						for (int j=0;j<Core.neighbors.size();j++){   // have a doubt on semaphore its better to lock client_connections before for loop
							send_vector_message.app_messages_sent = Core.send_vector[Core.neighbors.get(j)];
							try {								
								Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(send_vector_message);

							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						Core.sem_for_client_connections.release();
					}
				}
				Core.sem_for_no_of_nodes_count_for_recovery_initiator.release();
				Core.sem_for_number_of_rounds_in_recovery.release();
			}


		}

		
		//System.out.println("about to send last recv id msg");
		
		try {
			Core.sem_for_sent_last_recvId_msg.acquire();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(!Core.sent_last_recvId_msg) {
			Core.sent_last_recvId_msg = true;
			Message recv_id_msg = new Message();
			recv_id_msg.sourceId = Core.nodeId;
			recv_id_msg.MessageType = Core.LAST_RECV_ID_MSG_TYPE;

			//System.out.println("sending last recv id message to neighbors from initiator");
			try {
				Core.sem_for_client_connections.acquire();
			} catch (InterruptedException e1) {
				Core.sem_for_client_connections.release();
				e1.printStackTrace();
			}						
			for (int j=0;j<Core.neighbors.size();j++){   
				recv_id_msg.last_recv_message_id = Core.last_recv_id[Core.neighbors.get(j)];
				try {								
					Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(recv_id_msg);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			Core.sem_for_client_connections.release();
			
		}

		Core.sem_for_sent_last_recvId_msg.release();
		if(!gotlastrcvidmsg) {
			
			try {
				recvMessage = (Message) inStream.readObject();
				//System.out.println("received last recv id message from "+recvMessage.sourceId);
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		// calculate lost msg
		lost_message_cal(recvMessage);

		// receive decision made message
		temp = true;

		while(temp){

			// recv next message
			try {
				//System.out.println("Waiting for Decision made for lost msg or release msg");
				recvMessage = (Message) inStream.readObject();
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}


			if (recvMessage.MessageType == Core.RELEASE_MSG_TYPE) {
				
				//System.out.println("Got a release message from "+recvMessage.sourceId);
				break;
			}
			if(recvMessage.MessageType == Core.DECISION_MADE_MSG_TYPE) {
				try {
					Core.sem_for_no_of_nodes_count_for_recovery_initiator.acquire();
				} catch (InterruptedException e) {
					Core.sem_for_no_of_nodes_count_for_recovery_initiator.release();
					e.printStackTrace();
				}
				Core.no_of_nodes_count_for_recovery_initiator--;
				//System.out.println("Got a decision made message from "+recvMessage.sourceId+" after exchanging lost message");
				if(Core.no_of_nodes_count_for_recovery_initiator == 0) {
//					Core.sent_last_recvId_msg = false;
					Core.no_of_nodes_count_for_recovery_initiator = Core.node_count - 1;
					Core.checkpoint_counter = 0;
					// send release message to neighbor
					try {
						Core.sem_for_state.acquire();
					} catch (InterruptedException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}

					try {
						Core.sem_for_recoveries_completed.acquire();
					} catch (InterruptedException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					Core.recoveries_completed++;
					//System.out.println("Number of recoveries completed "+Core.recoveries_completed);
					Core.sem_for_recoveries_completed.release();


					Core.write_vector_clock_to_file();
					
					Message release_msg = new Message();
					release_msg.sourceId = Core.nodeId;
					release_msg.MessageType = Core.RELEASE_MSG_TYPE;
					try {
						Core.sem_for_client_connections.acquire();
					} catch (InterruptedException e1) {
						Core.sem_for_client_connections.release();
						e1.printStackTrace();
					}
					for (int j=0;j<Core.neighbors.size();j++){
						try {
							Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(release_msg);
							//System.out.println("sending release msg to "+Core.neighbors.get(j));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Core.sem_for_client_connections.release();
					Core.number_of_rounds_in_recovery = Core.node_count;
					Core.sem_for_failure.release();
					temp = false;

					// Core.write_vector_clock_to_file();


					//System.out.println("wrote vector clock to file");


					try {
						Core.sem_for_recoveries_completed.acquire();
					} catch (InterruptedException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					if(Core.recoveries_completed != Core.failure_count) {
						Core.sem_for_recoveries_completed.release();
						Core.state = Core.checkpoints.get(Core.checkpoints.size()-1).state;						
						if(Core.state == Core.ACTIVE_STATE) {
							//System.out.println("State after recovery changed to ACTIVE");
							Thread send_app = new Thread(new Application());
							send_app.start();
						}
						Core.sem_for_state.release();
						try {
							Core.sem_for_client_connections.acquire();
						} catch (InterruptedException e1) {
							Core.sem_for_client_connections.release();
							e1.printStackTrace();
						}
						//System.out.println("Sending lost messages");
						int number_of_lost_msg = Core.lost_messages.size();
						for (int j=0;j<number_of_lost_msg;j++){
							try {								
								Core.client_connections.get(Core.lost_messages.get(j).destId).outputStream.writeObject(Core.lost_messages.get(j));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						Core.lost_messages.clear();
						Core.sem_for_client_connections.release();
					} else {
						Core.sem_for_recoveries_completed.release();
						Core.sem_for_state.release();
					}
					
					try {
						recvMessage = (Message) inStream.readObject();
						//System.out.println("Got release from "+recvMessage.sourceId);
						
					} catch (ClassNotFoundException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

				}
				Core.sem_for_no_of_nodes_count_for_recovery_initiator.release();
			}
		}
	}

	public void lost_message_cal(Message recvMessage) {
		ArrayList<Message> temp = Core.sent_message_log_for_lost_msg.get(recvMessage.sourceId);
		//System.out.println("Last recv id from "+recvMessage.sourceId+" is "+recvMessage.last_recv_message_id+" number of message in sent log "+temp.size());
		if(recvMessage.last_recv_message_id < temp.size()) {
			try {
				Core.sem_for_lost_messages.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Core.lost_messages.addAll(temp.subList(recvMessage.last_recv_message_id, temp.size())); // addAll
			//System.out.println("Number of Lost message "+Core.lost_messages.size());
			Core.sem_for_lost_messages.release();
		}
	}

	public void recovery(Message recvMessage) {
		Core.rolled_back_checkpoint_index = Core.checkpoints.size() - 1;
		//System.out.println("rollback index "+Core.rolled_back_checkpoint_index);
		while(true) {			
			if(recvMessage.MessageType == Core.START_RECOVERY_MSG_TYPE) {
				try {
					Core.sem_for_no_of_neighbors_count_for_recovery.acquire();
				} catch (InterruptedException e) {
					Core.sem_for_no_of_neighbors_count_for_recovery.release();
					e.printStackTrace();
				}
				//System.out.println("Got a start recovery from "+recvMessage.sourceId);
				Core.no_of_neighbors_count_for_recovery--;
				if(Core.parent_for_recovery == -1) {
					Core.parent_for_recovery = recvMessage.sourceId;
					//System.out.println("parent "+Core.parent_for_recovery);
					// send recovery start message to all it neighbors
					//System.out.println("Sending start recovery to neigbors");
					recvMessage.sourceId = Core.nodeId;
					try {
						Core.sem_for_client_connections.acquire();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}
					for (int j=0;j<Core.neighbors.size();j++){
						try {
							Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(recvMessage);
							//System.out.println("sent start recovery to "+Core.neighbors.get(j));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Core.sem_for_client_connections.release();
				}
				if(Core.no_of_neighbors_count_for_recovery == 0) {
					Core.no_of_neighbors_count_for_recovery = Core.neighbors.size();
					// send decision made message to its parent with rolledback flag true
					Message decision_made_msg = new Message();
					decision_made_msg.sourceId = Core.nodeId;
					decision_made_msg.MessageType = Core.DECISION_MADE_MSG_TYPE;
					decision_made_msg.rolledback = true;
					//System.out.println("Sending decision made message to parent "+Core.parent_for_recovery);
					try {					
						Core.sem_for_client_connections.acquire();
						Core.client_connections.get(Core.parent_for_recovery).outputStream.writeObject(decision_made_msg);
						Core.sem_for_client_connections.release();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}
				}
				//System.out.println("Done with processing start recovery message");
				Core.sem_for_no_of_neighbors_count_for_recovery.release();

			} else if(recvMessage.MessageType == Core.NUMBER_OF_SEND_MSG_TYPE) {
				//System.out.println("Got a no of send msg from "+recvMessage.sourceId);
				try {
					Core.sem_for_no_of_neighbors_count_for_recovery.acquire();
				} catch (InterruptedException e) {
					Core.sem_for_no_of_neighbors_count_for_recovery.release();
					e.printStackTrace();
				}
				Core.no_of_neighbors_count_for_recovery--;
				Core.rolled_back_flag_for_recovery_intitator = Core.rolled_back_flag_for_recovery_intitator | rollback_checkpoint(recvMessage.app_messages_sent, recvMessage.sourceId);
				if(!Core.flag_to_track_first_message_for_recovery) {
					Core.flag_to_track_first_message_for_recovery = true;
					// send send message to its neighbors
					//System.out.println("Sending send vector msg to neigbors");
					recvMessage.sourceId = Core.nodeId;
					try {
						Core.sem_for_client_connections.acquire();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}
					for (int j=0;j<Core.neighbors.size();j++){
						recvMessage.app_messages_sent = Core.send_vector[Core.neighbors.get(j)];
						try {
							//System.out.println("Sending number of send msg to "+Core.neighbors.get(j));
							Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(recvMessage);							
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Core.sem_for_client_connections.release();
				}
				// roll back to new checkpoint
				if(Core.no_of_neighbors_count_for_recovery == 0) {
					Core.flag_to_track_first_message_for_recovery = false;
					Core.no_of_neighbors_count_for_recovery = Core.neighbors.size();
					// make new checkpoint as current checkpoint
					Core.rollback_checkpoint(Core.rolled_back_checkpoint_index+1);
					Message decision_made_msg = new Message();
					decision_made_msg.sourceId = Core.nodeId;
					decision_made_msg.MessageType = Core.DECISION_MADE_MSG_TYPE;
					decision_made_msg.rolledback = Core.rolled_back_flag_for_recovery_intitator; // change it to true or false based on rollback
					//System.out.println("rollback flag is "+Core.rolled_back_flag_for_recovery_intitator);
					Core.rolled_back_flag_for_recovery_intitator = false;
					//System.out.println("Sending decision made message for send vector msg to parent "+Core.parent_for_recovery+" with roll back flag "+decision_made_msg.rolledback);
					try {
						Core.sem_for_client_connections.acquire();					
						Core.client_connections.get(Core.parent_for_recovery).outputStream.writeObject(decision_made_msg);
						Core.sem_for_client_connections.release();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}
				}
				Core.sem_for_no_of_neighbors_count_for_recovery.release();

			} else if(recvMessage.MessageType == Core.DECISION_MADE_MSG_TYPE) {
				// send the recv message to its parent
				//System.out.println("Got a decision made message from "+recvMessage.sourceId+" forwarding it to parent "+Core.parent_for_recovery);
				try {					
					Core.sem_for_client_connections.acquire();					
					Core.client_connections.get(Core.parent_for_recovery).outputStream.writeObject(recvMessage);
					Core.sem_for_client_connections.release();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					Core.sem_for_client_connections.release();
					e.printStackTrace();
				}
			} else if(recvMessage.MessageType == Core.LAST_RECV_ID_MSG_TYPE) {
				try {
					Core.sem_for_no_of_neighbors_count_for_recovery.acquire();
				} catch (InterruptedException e) {
					Core.sem_for_no_of_neighbors_count_for_recovery.release();
					e.printStackTrace();
				}
				Core.no_of_neighbors_count_for_recovery--;
				//System.out.println("Got last recv id msg from "+recvMessage.sourceId);
				// calculate number of lost messages
				lost_message_cal(recvMessage);
				if(!Core.flag_to_track_first_message_for_recovery) {
					Core.flag_to_track_first_message_for_recovery = true;
					// send recv id message to its neighbors
					recvMessage.sourceId = Core.nodeId;
					try {
						Core.sem_for_client_connections.acquire();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}
					for (int j=0;j<Core.neighbors.size();j++){
						recvMessage.last_recv_message_id = Core.last_recv_id[Core.neighbors.get(j)];
						try {
							//System.out.println("Sending last recv msg to "+Core.neighbors.get(j));
							Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(recvMessage);							
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Core.sem_for_client_connections.release();

				}
				if(Core.no_of_neighbors_count_for_recovery == 0) {
					Core.flag_to_track_first_message_for_recovery = false;
					Core.no_of_neighbors_count_for_recovery = Core.neighbors.size();
					Core.flag_to_avoid_sending_duplicate_release_and_lost_msg = false;
					// send decision made message to its parent					
					Message decision_made_msg = new Message();
					decision_made_msg.sourceId = Core.nodeId;
					decision_made_msg.MessageType = Core.DECISION_MADE_MSG_TYPE;
					//System.out.println("Sending decision made message from last recv id");
					Core.rolled_back_flag_for_recovery_intitator = false;
					try {
						Core.sem_for_client_connections.acquire();					
						Core.client_connections.get(Core.parent_for_recovery).outputStream.writeObject(decision_made_msg);
						Core.sem_for_client_connections.release();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						Core.sem_for_client_connections.release();
						e.printStackTrace();
					}					
				}
				Core.sem_for_no_of_neighbors_count_for_recovery.release();
			} else if(recvMessage.MessageType == Core.RELEASE_MSG_TYPE) {
				try {
					Core.sem_for_flag_to_avoid_sending_duplicate_release_and_lost_msg.acquire();
				} catch (InterruptedException e2) {
					Core.sem_for_flag_to_avoid_sending_duplicate_release_and_lost_msg.release();
					e2.printStackTrace();
				}
				//System.out.println("Got a release msg from "+recvMessage.sourceId);
				if(!Core.flag_to_avoid_sending_duplicate_release_and_lost_msg) {
					Core.no_of_neighbors_count_for_recovery = Core.neighbors.size();
					Core.flag_to_avoid_sending_duplicate_release_and_lost_msg = true;
					Core.checkpoint_counter = 0;
					try {
						Core.sem_for_recoveries_completed.acquire();
					} catch (InterruptedException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					Core.write_vector_clock_to_file();
					Core.recoveries_completed++;
					Core.sem_for_recoveries_completed.release();

					Core.parent_for_recovery = -1;
					Message release_msg = new Message();
					release_msg.sourceId = Core.nodeId;
					release_msg.MessageType = Core.RELEASE_MSG_TYPE;
					//System.out.println("sending release message to neighbors");
					try {
						Core.sem_for_client_connections.acquire();
					} catch (InterruptedException e1) {
						Core.sem_for_client_connections.release();
						e1.printStackTrace();
					}
					for (int j=0;j<Core.neighbors.size();j++){
						try {
							Core.client_connections.get(Core.neighbors.get(j)).outputStream.writeObject(release_msg);
							//System.out.println("sending release message to "+Core.neighbors.get(j));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Core.sem_for_client_connections.release();
					Core.sem_for_failure.release();
					//System.out.println("Sem_for_failure available permits"+Core.sem_for_failure.availablePermits());
					//System.out.println("Released sem_for failure");
//					Core.write_vector_clock_to_file();
					if(Core.recoveries_completed != Core.failure_count) {
						Core.state = Core.checkpoints.get(Core.checkpoints.size()-1).state;
						if(Core.state == Core.ACTIVE_STATE) {
							//System.out.println("State changed to active");
							Thread send_app = new Thread(new Application());
							send_app.start();
						}
						//System.out.println("Sending lost msg");
						try {
							Core.sem_for_client_connections.acquire();
						} catch (InterruptedException e1) {
							Core.sem_for_client_connections.release();
							e1.printStackTrace();
						}
						int number_of_lost_msg = Core.lost_messages.size();
						for (int j=0;j<number_of_lost_msg;j++){
							try {								
								Core.client_connections.get(Core.lost_messages.get(j).destId).outputStream.writeObject(Core.lost_messages.get(j));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						Core.lost_messages.clear();
						Core.sem_for_client_connections.release();
					} else {
						Core.state = Core.PASSIVE_STATE;
					}

				}
				Core.sem_for_flag_to_avoid_sending_duplicate_release_and_lost_msg.release();
				break;
			}
			try {
				//System.out.println("Waiting for recovery message");
				recvMessage = (Message) inStream.readObject();
			} catch (ClassNotFoundException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public boolean rollback_checkpoint(int no_of_app_sent_messages,int nodeId) {
		//System.out.println("rollback index at "+Core.rolled_back_checkpoint_index+" number of app messages sent "+no_of_app_sent_messages+" by node "+nodeId);
		//System.out.println("recv_vector "+Core.convert_to_string(Core.checkpoints.get(Core.rolled_back_checkpoint_index).recv_vector));
		if(Core.checkpoints.get(Core.rolled_back_checkpoint_index).recv_vector[nodeId] <= no_of_app_sent_messages) {
			return false;
		}
		//System.out.println("Going to for loop in rollback checkpoint");
		for(int i=Core.rolled_back_checkpoint_index;i>=0;i--) {
			if(Core.checkpoints.get(i).recv_vector[nodeId] <= no_of_app_sent_messages) {
				//System.out.println("rollback_index changed to "+i);
				Core.rolled_back_checkpoint_index = i;
				break;
			}
		}
		return true;
	}
}
