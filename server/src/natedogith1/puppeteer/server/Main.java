package natedogith1.puppeteer.server;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {
	
	private static Server server;
	public static Object runLock = new Object();
	public static boolean run = true;
	
	private static void printUsage() {
		System.out.println("program [port]");
	}
	
	public static void main(String args[]) {
		if ( args.length > 1 ) {
			printUsage();
			System.exit(1);
			return;
		}
		
		int port = -1;
		if ( args.length == 1 ) {
			try {
				port = Integer.valueOf(args[0]);
			} catch (NumberFormatException e) {
				System.out.println("invalid port number");
				System.exit(1);
				printUsage();
				return;
			}
		}
		
		try {
			server = new Server(port);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(2);
			return;
		}
		server.start();
		Thread thread = new Thread("Console"){
			@Override
			public void run() {
				handleConsole();
			}
		};
		thread.setDaemon(true);
		thread.start();
		synchronized(runLock) {
			try {
			while ( run )
				runLock.wait();
			} catch (InterruptedException e) {
				
			}
		}
	}
	
	private static void printHelp() {
		System.out.println("help        \t print this help message");
		System.out.println("exit        \t stop the server");
		System.out.println("hardExit    \t exit the VM, shutting down everything");
		System.out.println("listServices\t list all hosted services");
		System.out.println("getPort     \t print the port the server is running on");
	}
	
	private static void handleConsole() {
		@SuppressWarnings("resource")
		Scanner scanIn = new Scanner(System.in);
		while (true) {
			System.out.print(">");
			String line = scanIn.nextLine();
			Scanner scan = new Scanner(line);
			String command = scan.next();
			command = command.toLowerCase();
			if ( command.equals("help") ) {
				printHelp();
			} else if ( command.equals("exit") ) {
				server.stop();
				synchronized (runLock) {
					run = false;
					runLock.notifyAll();
				}
			} else if ( command.equals("hardexit") ) {
				System.exit(0);
			} else if ( command.equals("listservices") ) {
				List<HostInfo> list = server.getHostDatabase().search("*");
				int maxId = 0;
				for ( HostInfo info : list ) 
					maxId = Math.max(maxId, info.getId());
				int len = 0;
				while ( maxId > 0 ) {
					len++;
					maxId>>=4;
				}
				for ( HostInfo info : list ) {
					System.out.printf("0x%0"+len+"x : %s", info.getId(), info.getName());
				}
			} else if ( command.equals("getport") ) {
				System.out.println(server.getPort());
			} else {
				System.out.println("Unknown command '" + command + "'");
				System.out.println("type 'help' for a list of commands");
			}
			scan.close();
		}
	}
}
