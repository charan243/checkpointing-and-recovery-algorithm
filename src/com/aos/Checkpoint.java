package com.aos;

import java.util.ArrayList;

public class Checkpoint {
	public int[] send_vector;
	public int[] recv_vector;
	public int[] last_send_id;
	public int[] last_recv_id;
	public int[] vector_clock;

	public String str_send_vector;
	public String str_recv_vector;
	public String str_last_send_id;
	public String str_last_recv_id;
	public String str_vector_clock;
	public String str_nodeid_of_messages_to_send;
	
	public int messages_sent;
	public int state;
	ArrayList<Integer> nodeid_of_messages_to_send;
	public Checkpoint(int[] send_vector, int[] recv_vector, int[] last_send_id, int[] last_recv_id, int[] vector_clock,
			String str_send_vector, String str_recv_vector, String str_last_send_id, String str_last_recv_id,
			String str_vector_clock, int messages_sent, int state, ArrayList<Integer> nodeid_of_messages_to_send,String str_nodeid_of_messages_to_send) {
		super();
		this.send_vector = send_vector;
		this.recv_vector = recv_vector;
		this.last_send_id = last_send_id;
		this.last_recv_id = last_recv_id;
		this.vector_clock = vector_clock;
		this.str_send_vector = str_send_vector;
		this.str_recv_vector = str_recv_vector;
		this.str_last_send_id = str_last_send_id;
		this.str_last_recv_id = str_last_recv_id;
		this.str_vector_clock = str_vector_clock;
		this.messages_sent = messages_sent;
		this.state = state;
		this.nodeid_of_messages_to_send = nodeid_of_messages_to_send;
		this.str_nodeid_of_messages_to_send = str_nodeid_of_messages_to_send;
	}
	
}
