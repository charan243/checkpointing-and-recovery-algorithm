package com.aos;


import java.io.Serializable;

public class Message implements Serializable {
	public int sourceId;
	public int destId;
	public int MessageType;
	public int[] vector_ts;
	public int recovery_initiator_id;
	public int app_message_id;
	public int app_messages_sent;
	public int last_recv_message_id;
	public boolean rolledback;
}
