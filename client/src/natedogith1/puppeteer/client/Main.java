package natedogith1.puppeteer.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Main {

	private static Puppet puppet;
	private static Thread consoleThread;
	public static Object runLock = new Object();
	public static boolean run = true;
	private static Object pauseLock = new Object();
	private static boolean paused = false;
	private static List<Server> servers = new LinkedList<Server>();
	private static List<Client> clients = new LinkedList<Client>();
	
	private static void printUsage() {
		System.out.println("program <host> [port]");
	}
	
	public static void main(String args[]) {
		if ( args.length < 1 || args.length > 2 ) {
			printUsage();
			System.exit(1);
			return;
		}
		
		int port = -1;
		if ( args.length == 2 ) {
			try {
				port = Integer.valueOf(args[1]);
			} catch (NumberFormatException e) {
				System.out.println("invalid port number");
				printUsage();
				System.exit(1);
				return;
			}
		}
		
		puppet = new Puppet(args[0], port);
		try {
			puppet.start();
		} catch (IOException e) {
			System.out.println("Could not connect to server: " + e.getMessage());
			System.exit(2);
			return;
		}
		consoleThread = new Thread("Console"){
			@Override
			public void run() {
				handleConsole();
			}
		};
		consoleThread.setDaemon(true);
		consoleThread.start();
		synchronized(runLock) {
			try {
			while ( run )
				runLock.wait();
			} catch (InterruptedException e) {
				
			}
		}
	}
	
	private static void pause() {
		synchronized (pauseLock) {
			paused = true;
			try{
				while( paused ) {
					pauseLock.wait();
				}
			} catch (InterruptedException e) {
				// we're interrupted, continue on
			}
		}
	}
	
	private static void unpause() {
		synchronized (pauseLock) {
			try{
				while( paused ) {
					pauseLock.wait();
				}
			} catch (InterruptedException e) {
				// we're interrupted, continue on
			}
			paused = false;
		}
	}
	
	private static void printHelp() {
		System.out.println("help             \t print this help message");
		System.out.println("exit             \t stop the server");
		System.out.println("hardExit         \t exit the VM, shutting down everything");
		System.out.println("listLocalServices\t list services this program is aware it's hosting");
		System.out.println("lookup <query>   \t list all services matching the query");
		System.out.println("server add <host> <port> <name>");
		System.out.println("\t adds a server under 'name' that connects to 'host' on 'port'");
		System.out.println("server list      \t list all hosted servers");
		System.out.println("server remove <host> <port> <name>");
		System.out.println("\t removes the coresponding server");
		System.out.println("client add <port> [id] <name>");
		System.out.println("\t adds a client listening on 'port' and connecting to server 'name' ");
		System.out.println("client list      \t list all clients");
		System.out.println("client remove <port> [id] <name>");
		System.out.println("\t removes the coresponding client");
	}
	
	private static void exit() {
		puppet.close();
		synchronized (runLock) {
			run = false;
			runLock.notifyAll();
		}
		
	}
	
	private static void printIds(Collection<ServerId> ids) {
		int maxId = 0;
		for ( ServerId id : ids ) 
			maxId = Math.max(maxId, id.getId());
		int len = 0;
		while ( maxId > 0 ) {
			len++;
			maxId>>=4;
		}
		for ( ServerId id : ids ) {
			System.out.printf("0x%0"+len+"x : %s\n", id.getId(), id.getName());
		}
	}
	
	private static void doLookup(String query) {
		puppet.lookup(query, new IListener.ListenerAdapter() {
			@Override
			public void lookupReply(ServerId[] servers) {
				printIds(Arrays.asList(servers));
				unpause();
			}
		});
		pause();
	}
	
	private static void printBadCommand(String command) {
		System.out.println("Unknown command '" + command + "'");
		System.out.println("type 'help' for a list of commands");
	}
	
	private static void clientCommand(String args) {
		try(Scanner scan = new Scanner(args);) {
		String subCom = scan.next().toLowerCase();
			if ( subCom.equals("add") ) {
				int port = scan.nextInt();
				boolean validId = scan.hasNextInt();
				int id = validId ? scan.nextInt(16) : 0;
				String name = scan.nextLine();
				Client c = validId? new Client(puppet, name, port, id) : 
					new Client(puppet, name, port);
				clients.add(c);
			} else if ( subCom.equals("list") ) {
				for ( Client c : clients )
					System.out.printf("%s : 0x%x receiving from port %d", c.getName(),
							c.getId(),  c.getPort());
			} else if ( subCom.equals("remove") ) {
				int port = scan.nextInt();
				boolean validId = scan.hasNextInt();
				int id = validId ? scan.nextInt(16) : 0;
				String name = scan.nextLine();
				Iterator<Client> iter = clients.iterator();
				while ( iter.hasNext() ) {
					Client c = iter.next();
					if ( c.hasId() == validId && (!validId || id == c.getId()) && 
							c.getPort() == port && c.getName().equals(name) ) {
						iter.remove();
						c.stop();
						break;
					}
				}
			} else {
				printBadCommand("client " + subCom);
			}
		} catch (NumberFormatException e) {
			
		}
		
	}
	
	private static void serverCommand(String args) {
		try(Scanner scan = new Scanner(args);) {
		String subCom = scan.next().toLowerCase();
			if ( subCom.equals("add") ) {
				String host = scan.next();
				int port = scan.nextInt();
				String name = scan.nextLine();
				Server s = new Server(puppet, name, host, port);
				servers.add(s);
			} else if ( subCom.equals("list") ) {
				for ( Server s : servers )
					System.out.printf("%s : 0x%x connecting to %s:%d", s.getName(),
							s.getId(), s.getName(), s.getPort());
			} else if ( subCom.equals("remove") ) {
				int id = scan.nextInt(16);
				String name = scan.nextLine();
				Iterator<Server> iter = servers.iterator();
				while ( iter.hasNext() ) {
					Server s = iter.next();
					if ( s.getId() == id && s.getName().equals(name) ) {
						iter.remove();
						s.stop();
						break;
					}
				}
			} else {
				printBadCommand("server " + subCom);
			}
		} catch (NumberFormatException e) {
			
		}
	}
	
	private static void handleConsole() {
		@SuppressWarnings("resource")
		Scanner scanIn = new Scanner(System.in);
		while (true) {
			System.out.print(">");
			String line = scanIn.nextLine();
			try ( Scanner scan = new Scanner(line); ) {
				String command = scan.next();
				command = command.toLowerCase();
				if ( command.equals("help") ) {
					printHelp();
				} else if ( command.equals("exit") ) {
					exit();
				} else if ( command.equals("hardexit") ) {
					System.exit(0);
				} else if ( command.equals("listLocalServices") ) {
					printIds(puppet.getServers().keySet());
				} else if ( command.equals("lookup") ) {
					doLookup(scan.nextLine());
				} else if ( command.equals("client" ) ) {
					clientCommand(scan.nextLine());
				} else if ( command.equals("server" ) ) {
					serverCommand(scan.nextLine());
				} else {
					printBadCommand(command);
				}
			} catch ( InputMismatchException e ) {
				System.out.println("Malformed number");
			}
		}
	}
}
