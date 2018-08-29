package com.aos;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Connection_details {
	Socket sock;
	ObjectOutputStream outputStream;
	ObjectInputStream inStream;
	public Connection_details(Socket sock, ObjectOutputStream outputStream, ObjectInputStream inStream) {
		super();
		this.sock = sock;
		this.outputStream = outputStream;
		this.inStream = inStream;
	}
	
}
