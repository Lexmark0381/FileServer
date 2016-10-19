import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;

class MainLoop implements Runnable {
    java.net.ServerSocket server_port;
    File MonitorDirectory;
    MainLoop(java.net.ServerSocket p, File directory) {
        MonitorDirectory = directory;
        server_port = p;
    }
    public void run(){
        Socket connection = null;
        InputStream input = null;
        InputStreamReader inR = null;
        OutputStream output = null;
        OutputStreamWriter ouR = null;
        BufferedReader br = null;
        BufferedWriter bw = null;

            while (true) {
                try {
                    //  INIT SERVER SOCKET
                    connection = server_port.accept();
                    String ClientId = connection.getInetAddress().toString().substring(1, connection.getInetAddress().toString().length());

                    System.out.println("connection accepted from " + ClientId);

                    input = connection.getInputStream();
                    inR = new InputStreamReader(input);

                    output = connection.getOutputStream();

                    br = new BufferedReader(inR);

                    char[] buff = new char[100];

                    //  INIT SERVER VARIABLES
                    String command = "";
                    int n;
                    File f = null;


                    //  READ COMMAND
                    n = br.read(buff, 0, 100);
                    if (n == -1) {
                        System.out.println(ClientId + " disconnected!");
                        break;
                    }
                    command = new String(buff, 0, n - 1);
                    String[] ArrayOfCommands = command.split(" ");

                    //  FORMAT COMMAND
                    System.out.println(ClientId + " : " + command);
                    ArrayList<String> argv = new ArrayList<String>(10);

                    for (int i = 0; i < command.split(" ").length; i++) {
                        argv.add(i, ArrayOfCommands[i]);
                    }


                    if ((argv.get(0).equals("copy") || argv.get(0).equals("list"))) {
                        if (argv.get(0).equals("copy")) {
                            if (argv.size() == 3) {

//                              RECEIVING FILE!
                                System.out.println("File Incoming!");
                                String FileName = argv.get(1);
                                String FileSize = argv.get(2);
                                System.out.println(FileName + " incoming! It's "+ FileSize + " bytes long!");

                                sendToClient("OK\n", output);
//                                output.flush();
                                System.out.println("Receiving "+ FileName);

                                FileOutputStream fos = new FileOutputStream(MonitorDirectory + "/" +FileName);

                                byte[] block = new byte[4096];
                                int size;

                                while ((size = input.read(block)) > 0) {
                                    fos.write(block, 0, size);
                                }

                                fos.flush();
                                fos.close();

                                System.out.println(FileName + " received!");
                            } else {

//                              SENDING FILE!
                                File file = new File(argv.get(2));
                                if (!(isThereFile(file)) || file.isDirectory()) {
                                    String ErrorMessage = argv.get(1) + " : " + file.getName() + " doesn't exist or is a Directory!\n";
                                    sendToClient(ErrorMessage, output);
                                    System.out.println(ErrorMessage);
                                    output.flush();
                                    connection.close();
                                    continue;
                                }
//                              THE FILE IS OKAY, CONTINUE!
                                System.out.println("File OK!");

                                sendToClient("File OK!\n", output);
                                output.flush();
//                                }

                                ArrayList<String> Targets = new ArrayList<String>();
                                for (int i = 3; i < argv.size(); i++) {
                                    Targets.add(argv.get(i));
                                }
                                sendToClient("Beginning to send...",output);
                                int JobsDone = 0;
                                for (String target : Targets) {
                                    Socket TargetConnection = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
//                                    System.out.println("created TargetConnection");
                                    InputStream TargetInput = TargetConnection.getInputStream();
//                                    System.out.println("created TargetInput");
                                    OutputStream Targetoutput = TargetConnection.getOutputStream();
//                                    System.out.println("created Targetoutput");
                                    PrintWriter out = new PrintWriter(Targetoutput);
//                                    System.out.println("created out");
                                    System.out.println("Preparing "+target+" to receive...");
                                    String CommandToSend = "copy " + file.getName() + " " + file.length();
                                    System.out.println("Sending command \'"+ CommandToSend + "\' to " + target);
                                    out.println(CommandToSend);
                                    System.out.println("Command sent!");
                                    out.flush();

                                    BufferedReader reader = new BufferedReader(new InputStreamReader(TargetInput));
                                    String response = reader.readLine();
//                                    String line;
//                                    while ((line = ) != null) {
//                                        response += line;
//                                    }
                                    if(!response.equals("OK")){
                                        System.out.println("Invalid Response: Received: " + response);
                                        continue;
                                    }
                                    System.out.println("Response OK received");

                                    // begin file sending..
                                    System.out.println("Sending "+ file + " to "+ target);
                                    DataOutputStream dos = new DataOutputStream(TargetConnection.getOutputStream());
                                    FileInputStream fis = new FileInputStream(file);

                                    byte[] buffer = new byte[4096];
                                    int size = 0;
                                    while ((size = fis.read(buffer)) > 0) {
//                                        System.out.println("'" + size + "'");
                                        dos.write(buffer, 0, size);
                                    }
                                    dos.flush();
                                    fis.close();
                                    dos.close();

                                    // .. end it!


                                    out.close();
                                    TargetConnection.close();

                                    System.out.println("File sent to " + target);
                                    JobsDone ++;
                                    sendToClient(target + " : " + file + " received!\n", output );
                                }

                                    System.out.println("File sent to "+ JobsDone + " targets!");
                                    sendToClient("Job Done!\n", output);



                            }
                        }
                        if (argv.get(0).equals("list")) {
                            File[] files = MonitorDirectory.listFiles();
                            try {
                                printFilesInDirectory(files, 0, output);
                            } catch (Exception e) {
                                System.out.println("Error while listing: " + e);
                            }
                            output.flush();
                        }
                    }
                    if (!(argv.get(0).equals("list") || argv.get(0).equals("copy"))) {
                        System.out.println(command + ": command not found.");
                        sendToClient(command + ": command not found.\n", output);


                    }
                    connection.close();
                    System.out.println("Connection with " + ClientId + " closed");
                } catch (IOException ex) {
                    System.out.println("Error while starting thread: " + ex);
                }

            }

    }


    public void printFilesInDirectory(File[] files, int tabs, java.io.OutputStream output) throws Exception{
        String indentation = "";
        for (int i = 0; i < tabs; i++){
            indentation += (char) 9;
            indentation += '|';
        }

        for (File file : files){
            if(file.isDirectory()){
//                System.out.println(indentation + file + "[DIRECTORY]");
                sendToClient(indentation + file.getPath() + "\n", output);
                File[] rec = file.listFiles();
                printFilesInDirectory(rec, tabs+1, output);
            } else {
                try {
                    sendToClient(indentation + file.getPath() + "\n", output);
                } catch (Exception ex){
                    System.out.println("Error while sending data to client: "+ ex);
                }
            }
        }

    }
    public void sendToClient(String message, java.io.OutputStream output) throws IOException{
            output.write(message.getBytes(Charset.forName("UTF-8")));
    }
    public boolean isThereFile(File file){
        if (file == null){
            return false;
        }
        if (file.exists()){
            return true;
        } else {
            return false;
        }
    }


}
class FileServer {
    public static void main(String [] args) throws Exception{
        int PortNumber = 9999;
        String MonitorDirectory = ".";
        java.net.ServerSocket server_port;
      if(args.length > 2){
          System.out.println("USAGE: java FileServer PARAMS\nPARAMS: Port number, Monitor Directory or both.");
          System.exit(1);
      }
        if(args.length == 1){
            try{
                PortNumber = Integer.parseInt(args[0]);
            }
            catch (Exception ex){
                MonitorDirectory = args[0];
            }
        }
        if(args.length == 2){
            try{
                PortNumber = Integer.parseInt(args[0]);
                MonitorDirectory = args[1];
            } catch (Exception ex){
                PortNumber = Integer.parseInt(args[1]);
                MonitorDirectory = args[0];
            }

        }
        try{
            File f = new File(MonitorDirectory);
        } catch (Exception ex){
            System.out.println(MonitorDirectory + " doesn't exist. Using Default.");
            MonitorDirectory = ".";
        }

        try{
            server_port = new java.net.ServerSocket(PortNumber);
        } catch (java.io.IOException ex) {
            System.err.println("Error while creating server socket: " +ex);
            System.exit(1);
            return;
        }
        File FileMonitorDirectory = new  File(MonitorDirectory);
        System.out.println("Server Running. Port number: "+ PortNumber + " Monitoring directory '" + FileMonitorDirectory.getCanonicalPath() + "'");
        Thread T[] = new Thread[10];
        for (int i = 0; i < 10; i++){
            T[i] = new Thread(new MainLoop(server_port, FileMonitorDirectory));
            T[i].start();
        }
        for (int i=0; i<10; i++){
            T[i].join();
        }
        try{
            server_port.close();
        } catch (java.io.IOException ex) {
            System.err.println("Error while closing server socket: " +ex);
            System.exit(1);
        }

    }
}