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
                    //  INIT SERVER SOCKETS
                    connection = server_port.accept();
                    String ClientId = connection.getInetAddress().toString().substring(1, connection.getInetAddress().toString().length());

                    System.out.println("connection accepted from " + ClientId);

                    input = connection.getInputStream();
                    inR = new InputStreamReader(input);

                    output = connection.getOutputStream();
                    ouR = new OutputStreamWriter(output);

                    br = new BufferedReader(inR);


                    char[] buff = new char[100];

                    //  INIT SERVER VARIABLES
                    String command = "";
                    int n;
                    int data = 0;
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

                    for (int i = 0; i < ArrayOfCommands.length; i++) {
                        argv.add(i, ArrayOfCommands[i]);
                    }


                    if ((argv.get(0).equals("copy") || argv.get(0).equals("list"))) {
                        if (argv.get(0).equals("copy")) {
                            if (argv.size() == 3) {

//                              RECEIVING FILE!
                                System.out.println("File Incoming!");
                                String FileName = argv.get(1);
                                int FileSize = Integer.parseInt(argv.get(2));
                                System.out.println(FileName + " incoming! It's "+ FileSize + " bytes long!");

                                sendToClient("OK\n", output);
                                output.flush();
                                System.out.println("Receiving "+ FileName);


//                                System.out.println("Opened DataInputStream");
                                FileOutputStream fos = new FileOutputStream(MonitorDirectory + "/" +FileName);
                                System.out.println("Opened FileOutputStream");
//                                char[] buffer = new char[4096];
                                System.out.println("Created buffer");

                                byte[] block = new byte[4096];
                                int size;

                                while ((size = input.read(block)) > 0) {
//                                    System.out.println("'" + size + "'");
                                    fos.write(block, 0, size);
                                }

                                fos.flush();
                                fos.close();
                                br.close();

                                System.out.println(FileName + " received!");
                            } else {

//                              SENDING FILE!
                                File file = new File(argv.get(2));
                                System.out.println("Checking file correctness...");
                                if (!(isThereFile(file)) || file.isDirectory()) {

                                    System.out.println(file.getName() + " doesn't exist or is a Directory!");
                                    sendToClient(argv.get(1) + " : " + file.getName() + " doesn't exist or is a Directory!", output);
                                    continue;
                                }
                                System.out.println("File OK!");

//                                }
                                System.out.println("Retrieving targets...");
                                ArrayList<String> Targets = new ArrayList<String>();
                                for (int i = 3; i < argv.size(); i++) {
                                    Targets.add(argv.get(i));
                                }
                                System.out.println("Targets retrieved!");
                                System.out.println("Knocking...");
                                for (String target : Targets) {
                                    try {
                                        System.out.println("Knocking at " + target.split(":")[0] + ":" + Integer.parseInt(target.split(":")[1]));
                                        Socket socket = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
//                                        wait(2);
                                        socket.close();
                                        System.out.println(target + " is online!");
                                    } catch (Exception ex) {
                                        System.out.println("Couldn't knock! on  " + target + " : " + ex);
                                    }
                                }
                                System.out.println("Targets are online!");




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
                                    System.out.println("Sending \'"+ CommandToSend + "\' to " + target);
                                    out.println(CommandToSend);
                                    System.out.println("Sent!");
                                    out.flush();

                                    BufferedReader reader = new BufferedReader(new InputStreamReader(TargetInput));
                                    String response = reader.readLine();
//                                    String line;
//                                    while ((line = ) != null) {
//                                        response += line;
//                                    }
                                    System.out.println("Received response: " + response);
                                    if(!response.equals("OK")){
                                        System.out.println("Invalid Response: Received: " + response);
                                        break;
                                    }
                                    System.out.println("Response OK received");

                                    // begin file sending..
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
                                }
                                System.out.println("File sent to all the targets!");
                                sendToClient("Job Done!", output);


                            }
                        }
                        if (argv.get(0).equals("list")) {

                            f = MonitorDirectory;
                            File[] files = f.listFiles();
                            try {
                                printFilesInDirectory(files, 0, output);
                            } catch (Exception e) {
                                System.out.println("Error while listing: " + e);
                            }
                            output.close();
                            ouR.close();

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
        int PortNumber = 0;
        File MonitorDirectory = null;
        java.net.ServerSocket server_port;
        if(args.length == 0){
            PortNumber = 9999;
            MonitorDirectory = new File(".");
        }
        if(args.length == 1){
            PortNumber = Integer.valueOf(args[0]) ;
            MonitorDirectory = new File(".");
        }
        if(args.length == 2){
            PortNumber = Integer.valueOf(args[0]) ;
            File Root = new File(".");

            File f = new File(args[1]);

            if (f.exists()){
                MonitorDirectory = new File(args[1]);
            } else {
                System.out.println("'" + args[1] + "' doesn't exist.");
                System.exit(1);
            }

        }

        try{
            server_port = new java.net.ServerSocket(PortNumber);
        } catch (java.io.IOException ex) {
            System.err.println("Error while creating server socket: " +ex);
            System.exit(1);
            return;
        }
        System.out.println("Server Running. Port number: "+ PortNumber + " Monitoring directory '" + MonitorDirectory.getCanonicalPath() + "'");
        Thread T[] = new Thread[10];
        for (int i = 0; i < 10; i++){
            T[i] = new Thread(new MainLoop(server_port, MonitorDirectory));
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