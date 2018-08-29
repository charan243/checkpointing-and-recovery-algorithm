package com.aos;

import java.io.*;
import java.net.*;
import java.util.Scanner;
public class Client 
{
	public void go()
	{
		String message;
		try
		{
			Socket clientSocket = new Socket("dc02",1234);
			ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
			//message = reader.readLine();
			//System.out.println("Server says:" + message);
			//reader.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}
	public static void main(String args[])
	{
		Client SampleClientObj = new Client();
		SampleClientObj.go();
	}
}