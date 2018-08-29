package com.aos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Get_connections implements Runnable {

	public void run() {
		/*
		int number_of_neighbors = Core.neighbors.size();
		int port = Integer.parseInt(Core.location.get(Core.nodeId).get(1));
		try
		{
			System.out.println("Listening on port:"+port);
			//Create a server socket at port 5000
			ServerSocket serverSock = new ServerSocket(port);
			//Server goes into a permanent loop accepting connections from clients			
			while(true)
			{
				//Listens for a connection to be made to this socket and accepts it
				//The method blocks until a connection is made
				Socket sock = serverSock.accept();
				//PrintWriter is a bridge between character data and the socket's low-level output stream
//				ObjectInputStream inStream = new ObjectInputStream(sock.getInputStream());
				number_of_neighbors--;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		int number_of_neighbors = Core.neighbors.size();
		ServerSocket serverSock = null;
		try {
			int port = Integer.parseInt(Core.location.get(Core.nodeId).get(1));
			//System.out.println("Server listening on "+port);

			serverSock = new ServerSocket(port);

			while(number_of_neighbors > 0) {
				Socket sock = serverSock.accept();
				ObjectInputStream inStream = new ObjectInputStream(sock.getInputStream());
				Thread server_thread = new Thread(new Server_thread_for_client(new Connection_details(sock, null, inStream)));
				server_thread.start();
				number_of_neighbors--;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
