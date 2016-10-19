import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.TreeMap;

class MainLoop implements Runnable {
    java.net.ServerSocket server_port;
    File MonitorDirectory;

    MainLoop(java.net.ServerSocket p, File directory) {
        MonitorDirectory = directory;
        server_port = p;
    }

    public void run() {
        Socket connection = null;
        InputStream input = null;
        InputStreamReader inR = null;
        OutputStream output = null;
        OutputStreamWriter ouR = null;
        BufferedReader br = null;
        BufferedWriter bw = null;

        while (true) {
            try {

//              INIT SERVER SOCKET
                connection = server_port.accept();
                String ClientId = connection.getInetAddress().toString().substring(1, connection.getInetAddress().toString().length());
                System.out.println("connection accepted from " + ClientId);

                input = connection.getInputStream();
                inR = new InputStreamReader(input);
                output = connection.getOutputStream();
                br = new BufferedReader(inR);
                char[] buff = new char[100];

//              INIT SERVER VARIABLES
                String command;
                int n;


//              READ COMMAND
                n = br.read(buff, 0, 100);
                if (n == -1) {
                    System.out.println(ClientId + " disconnected!");
                    continue;
                }
                command = new String(buff, 0, n - 1);
                String[] ArrayOfCommands = command.split(" ");

//              FORMAT COMMAND
                System.out.println(ClientId + " : " + command);
                ArrayList<String> argv = new ArrayList<String>(10);

                for (int i = 0; i < command.split(" ").length; i++) {
                    argv.add(i, ArrayOfCommands[i]);
                }

//              CHECK WHAT IS READ BY THE CONSOLE
                if (argv.get(0).equals("copy") || argv.get(0).equals("list")) {

//                  WE RECEIVED A COPY COMMAND
                    if (argv.get(0).equals("copy")) {

//                      IF THERE ARE NO TARGET, IT MEANS THAT THE SERVER IS GOING TO RECEIVE A FILE
                        if (argv.size() == 3) {

//                          WE RECEIVED A COMMAND FOR RECEIVING A FILE
                            System.out.println("File Incoming!");
                            String FileName = argv.get(1);
                            String FileSize = argv.get(2);
                            System.out.println(FileName + " incoming! It's " + FileSize + " bytes long!");

//                          WE TELL TO THE SERVER THAT IS TRYING TO SEND US THE FILE THAT WE ARE READY FOR RECEIVING.
                            sendToClient("OK\n", output);
                            System.out.println("Receiving " + FileName);

//                          WE START RECEIVING THE FILE
                            FileOutputStream fos = new FileOutputStream(MonitorDirectory + "/" + FileName);

                            byte[] block = new byte[4096];
                            int size;

                            while ((size = input.read(block)) > 0) {
                                fos.write(block, 0, size);
                            }

                            fos.flush();
                            fos.close();

//                          WE RECEIVED THE FILE
                            System.out.println(FileName + " received!");
                        } else {

//                          IF IN THE COMMAND RECEIVED THERE IS AT LEAST A TARGET, IT MEANS THAT WE ARE GOING TO SEND THE FILE
                            File file = new File(argv.get(2));

//                          CHECK IF THE FILE EXISTS AND IT'S NOT A DIRECTORY: IF NOT, TERMINATE.
                            if (!(isThereFile(file)) || file.isDirectory()) {
                                String ErrorMessage = argv.get(1) + " : " + file.getName() + " doesn't exist or is a Directory!\n";
                                sendToClient(ErrorMessage, output);
                                System.out.println(ErrorMessage);
                                output.flush();
                                connection.close();
                                continue;
                            }

//                          THE FILE IS OKAY, CONTINUE!
                            System.out.println("File OK!");

//                          SAY TO THE FILECONTROLLER THAT THE FILE IS "GOOD"
                            sendToClient(argv.get(1) + " : File OK!\n", output);
                            output.flush();

//                          ARRAYLIST CONTAINING ALL THE SERVERS THAT SHOULD RECEIVE THE FILE
                            ArrayList<String> Targets = new ArrayList<String>();
                            for (int i = 3; i < argv.size(); i++) {
                                Targets.add(argv.get(i));
                            }
                            int JobsDone = 0;

//                          SEND THE FILE TO ALL THE TARGETS
                            for (String target : Targets) {

//                              SETTING UP CONNECTION FOR A TARGET
                                sendToClient("Sending to " + target + "\n", output);
                                Socket TargetConnection = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
                                InputStream TargetInput = TargetConnection.getInputStream();
                                OutputStream Targetoutput = TargetConnection.getOutputStream();
                                PrintWriter out = new PrintWriter(Targetoutput);

//                              SENDING COMMAND TO THE TARGET FOR PREPARING IT TO RECEIVE
                                System.out.println("Preparing " + target + " to receive...");

//                              THE COMMAND IS "copy FileName FileSize"
                                String CommandToSend = "copy " + file.getName() + " " + file.length();
                                System.out.println("Sending command \'" + CommandToSend + "\' to " + target);

//                              THE COMMAND IS SENT TO THE TARGET
                                out.println(CommandToSend);
                                System.out.println("Command sent!");
                                out.flush();

//                              ONCE THE COMMAND HAS BEEN SENT, WE EXPECT TO RECEIVE A RESPONSE
                                BufferedReader reader = new BufferedReader(new InputStreamReader(TargetInput));
                                String response = reader.readLine();

//                              THE RESPONSE MUST BE AN "OK"
                                if (!response.equals("OK")) {
                                    System.out.println("Invalid Response: Received: " + response);
                                    continue;
                                }

//                              THE TARGET IS NOW EXPECTING TO RECEIVE A FILE
                                System.out.println("Response OK received");

                                // ACTUALLY SEND THE FILE TO THE TARGET
                                System.out.println("Sending " + file + " to " + target);
                                DataOutputStream dos = new DataOutputStream(TargetConnection.getOutputStream());
                                FileInputStream fis = new FileInputStream(file);

//                              TRANSFER THE FILE AS 4096B CHUNKS
                                byte[] buffer = new byte[4096];

//                              SIZE IS HOW MANY BYTES HAS BEEN READ
                                int size;

//                              READ FROM fis OR 4096B OR WHATEVER CAME
                                while ((size = fis.read(buffer)) > 0) {

//                                  WHATEVER IS RECEIVED IS WRITTEN INTO A FILE
                                    dos.write(buffer, 0, size);
                                }

//                              CLOSE NERWORK AND FILE STREAMS
                                dos.flush();
                                fis.close();
                                dos.close();
                                out.close();
                                TargetConnection.close();

//                              THE FILE HAS BEEN SENT TO THE TARGET
                                System.out.println("File sent to " + target);
                                JobsDone++;

//                              TELL TO THE FILECONTROLLER WHAT HAS BEEN DONE
                                sendToClient(target + " : " + file + " received!\n", output);
                            }

//                          FILE SENT TO ALL THE TARGETS...
                            System.out.println("File sent to " + JobsDone + " targets!");
//                                ...TELL THIS TO THE FILECONTROLLER
                            sendToClient("File sent to " + JobsDone + " targets!\n", output);


                        }
                    }

//                  RECEIVED A LIST COMMAND
                    if (argv.get(0).equals("list")) {

//                      LIST THE FILES IN THE DIRECTORY YOU ARE MONITORING
                        File[] files = MonitorDirectory.listFiles();
                        try {

//                          METHOD CALL
                            printFilesInDirectory(files, 0, output);
                        } catch (Exception e) {
                            System.out.println("Error while listing: " + e);
                        }
                        output.flush();
                    }
                } else {

//                  NEITHER LIST NOR FILE WAS THE COMMAND READ
                    System.out.println(command + ": command not found.");
                    sendToClient(command + ": command not found.\n", output);
                }
//              KEEP LOOPING WHILE THE CLIENT DISCONNECTS
                connection.close();
                System.out.println("Connection with " + ClientId + " closed");
            } catch (IOException ex) {
                System.out.println("Error while starting thread: " + ex);
            }
        }
    }


    public void printFilesInDirectory(File[] files, int tabs, java.io.OutputStream output) throws Exception {
        String indentation = "";
        for (int i = 0; i < tabs; i++) {
            indentation += (char) 9;
            indentation += '|';
        }

        for (File file : files) {
            if (file.isDirectory()) {
//                System.out.println(indentation + file + "[DIRECTORY]");
                sendToClient(indentation + file.getPath() + "\n", output);
                File[] rec = file.listFiles();
                printFilesInDirectory(rec, tabs + 1, output);
            } else {
                try {
                    sendToClient(indentation + file.getPath() + "\n", output);
                } catch (Exception ex) {
                    System.out.println("Error while sending data to client: " + ex);
                }
            }
        }

    }

    public void sendToClient(String message, java.io.OutputStream output) throws IOException {
        output.write(message.getBytes(Charset.forName("UTF-8")));
    }

    public boolean isThereFile(File file) {
        if (file == null) {
            return false;
        }
        if (file.exists()) {
            return true;
        } else {
            return false;
        }
    }


}

class FileServer {
    public static void main(String[] args) throws Exception {

//      DEFAULT RUN VALUES
        int PortNumber = 9999;
        String MonitorDirectory = ".";
        java.net.ServerSocket server_port;

//      TOO MANY PARAMETERS PASSED!
        if (args.length > 2) {
            System.out.println("USAGE: java FileServer PARAMS\nPARAMS: Port number, Monitor Directory or both.");
            System.exit(1);
        }

//      THE GIVEN PARAMETER WAS THE PORT OR THE MONITOR DIRECTORY?
        if (args.length == 1) {
            try {
                PortNumber = Integer.parseInt(args[0]);
            } catch (Exception ex) {
                MonitorDirectory = args[0];
            }
        }

//      WHICH OF THE PARAMETERS IS THE PORT AND WHICH IS THE MONITOR DIRECTORY?
        if (args.length == 2) {
            try {
                PortNumber = Integer.parseInt(args[0]);
                MonitorDirectory = args[1];
            } catch (Exception ex) {
                PortNumber = Integer.parseInt(args[1]);
                MonitorDirectory = args[0];
            }
        }

//      IF THE MONITOR DIRECTORY EXISTS, USE IT...
        try {
            File f = new File(MonitorDirectory);
        } catch (Exception ex) {
//          ... OTHERWISE USE THE DEFAULT ONE
            System.out.println(MonitorDirectory + " doesn't exist. Using Default.");
            MonitorDirectory = ".";
        }

//      TRY TO USE PortNumber TO START THE SERVER
        try {
            server_port = new java.net.ServerSocket(PortNumber);
        } catch (java.io.IOException ex) {

//          PROBABLY THE PORT IS BUSY...
            System.err.println("Error while creating server socket: " + ex);
            System.exit(1);
            return;
        }
        File FileMonitorDirectory = new File(MonitorDirectory);

//      ALL WENT FINE, START!
        System.out.println("Server Running. Port number: " + PortNumber + " Monitoring directory '" + FileMonitorDirectory.getCanonicalPath() + "'");
        Thread T[] = new Thread[10];
        for (int i = 0; i < 10; i++) {
            T[i] = new Thread(new MainLoop(server_port, FileMonitorDirectory));
            T[i].start();
        }
        for (int i = 0; i < 10; i++) {
            T[i].join();
        }
        try {
            server_port.close();
        } catch (java.io.IOException ex) {
            System.err.println("Error while closing server socket: " + ex);
            System.exit(1);
        }

    }
}