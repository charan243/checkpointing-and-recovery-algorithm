package com.aos;

import java.io.IOException;

public class Application implements Runnable {
	
	public void run() {
		int i=0;
		//System.out.println(Core.neighbors_to_send_app_msg);
		while(true) {
		
			try {
				Core.sem_for_state.acquire();
			} catch (InterruptedException e1) {
				Core.sem_for_state.release();
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if(Core.state != Core.ACTIVE_STATE || Core.neighbors_to_send_app_msg.size() == 0) { // add semaphores
				Core.sem_for_state.release();
				break;
			}
			
			Message message_to_send = new Message();
			message_to_send.destId = Core.neighbors_to_send_app_msg.get(i);
			message_to_send.sourceId = Core.nodeId;
			message_to_send.MessageType = Core.APP_MSG_TYPE;
			message_to_send.app_message_id = Core.last_send_id[Core.neighbors_to_send_app_msg.get(i)]+1;
			
			try {
				Core.sem_for_vector_clock.acquire();
				Core.sem_for_checkpoints.acquire();
			} catch (InterruptedException e) {
				Core.sem_for_checkpoints.release();
				Core.sem_for_vector_clock.release();
				e.printStackTrace();
			}
			Core.vector_clock[Core.nodeId]++;
			message_to_send.vector_ts = Core.vector_clock;	
			send(message_to_send);
			//System.out.println("Sent app msg to "+message_to_send.destId);
			Core.total_app_messages_sent++;
			Core.last_send_id[message_to_send.destId]++;
			Core.send_vector[message_to_send.destId]++;
			Core.sent_message_log_for_lost_msg.get(message_to_send.destId).add(message_to_send);
			Core.neighbors_to_send_app_msg.remove(i);	
			
			if(Core.neighbors_to_send_app_msg.size() == 0) {
				Core.state = Core.PASSIVE_STATE; 
				Core.save_checkpoint(Core.neighbors_to_send_app_msg);
				Core.sem_for_checkpoints.release();
				Core.sem_for_vector_clock.release();
				Core.sem_for_state.release();
				break;
			} else {
				boolean failure_event = Core.save_checkpoint(Core.neighbors_to_send_app_msg);
				if(!failure_event) {
					//System.out.println("Stopped sending app msg as failure was detected");
					Core.sem_for_checkpoints.release();
					Core.sem_for_vector_clock.release();
					Core.sem_for_state.release();
					break;
				}
			}
			Core.sem_for_checkpoints.release();
			Core.sem_for_vector_clock.release();
			Core.sem_for_state.release();			
			try {
				Thread.sleep(Core.minSendDelay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		//System.out.println("Done with sending app messages");
	}
	
	public void send(Message msg_to_send) {
		
		if (!Core.client_connections.get(msg_to_send.destId).sock.isClosed()){
		try {
			Core.sem_for_client_connections.acquire();
			Core.client_connections.get(msg_to_send.destId).outputStream.writeObject(msg_to_send);
			Core.sem_for_client_connections.release();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
	}
	
}
