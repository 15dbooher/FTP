/*  Daniel Booher
    djb150230
    Server class for the FTP
*/

import java.net.Socket;
import java.net.SocketException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;

public class Server{

    private ServerSocket server;
    private Socket connection;
    private DataInputStream input;
    private DataOutputStream output;
    private DataOutputStream fileOutput;
    private FileInputStream fileInput;
    private String checksum = "";
    private String fileToSend = "";
    private final int BUFFERSIZE = 8192;

    public Server(int p){
        start(p); //this is so that the program can run indefinetly
    }

    public void start(int port){

        try{
            while(true){ //run this program until user manually terminates it (so that it will stay running after disconnecting with a client)

            findAllInterfaceIP4();

            // Find public IP address 
            String systemipaddress = "Unknown"; 
            try
            { 
                URL url_name = new URL("http://bot.whatismyipaddress.com"); 
  
                BufferedReader sc = 
                new BufferedReader(new InputStreamReader(url_name.openStream())); 
  
                // reads system IPAddress 
                systemipaddress = sc.readLine().trim(); 
            } 
            catch (UnknownHostException se) 
            { 
                //System.out.println("\n" + se);
                System.out.println("\nYou are not connected to the internet and so your public IPv4 address could not be retrieved"); 
            }
            System.out.println(); 
            System.out.println("Public IP Address: " + systemipaddress +"\n");  
            
            //Create the server and accept the connection to the client
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            System.out.println("Starting server " + "on port " + server.getLocalPort() + "...\n");
            connection = server.accept(); //accept the connection to the client
            connection.setSoTimeout(1000 * 60); //set a timeout value equal to 1 minute
            System.out.println("Connected to client " + connection.getInetAddress().getHostAddress() + " on client port " + connection.getPort() + "...\n");
            input = new DataInputStream(connection.getInputStream());
            output = new DataOutputStream(connection.getOutputStream());
            fileOutput = new DataOutputStream(connection.getOutputStream());

            //Server sends the client a list of files available to download
            ArrayList<File> availableFiles = getAvailableFileList();
            //Send list of available files
            output.writeUTF("Files available: " + availableFiles.size());
            System.out.println("Files available: " + availableFiles.size() + "\n");
            for(File f : availableFiles){
                output.writeUTF(f.getName() + " Size: " + makeByteValueReadable(f.length()));
                System.out.println(f.getName() + " Size: " + makeByteValueReadable(f.length()) + "\n");
            }
            Boolean hasFile = getFileFromClient(availableFiles);
            if(!hasFile){ //if the file cannot be found, send the FNF flag
                output.writeUTF("FNF"); //File Not Found
                System.out.println("Client requested file " + fileToSend + " which is not in list\n");
            }
            else{ //if it is found, send the FF flag and then send the file
                output.writeUTF("FF"); //File Found
                System.out.println("Cient requested " + fileToSend + " to download...\n");
                //Send the requested file
                for(File f : availableFiles){
                    if(f.getName().equals(fileToSend)){
                        sendWholeFile(f);
                    }
                }
                
                String clientEOFT = "";
                clientEOFT = input.readUTF(); //listen for the client to be done recieving the file
                output.writeUTF("CS=" + checksum); //send the client the checksum for the file
                String clientCSACK = "";
                clientCSACK = input.readUTF(); //listen for the client's response about checksum
                if(clientCSACK.equals("CSNACK")){
                    //if checksum does not match, close the connection and restart server
                    System.out.println("Client Checksum does not match\n");
                }
                else if(clientCSACK.equals("CSACK")){
                    //if checksum matched, then close connection and restart server
                    System.out.println("Client Checksum matched!\n");
                }
            }
            
            

            closeStuff();
            
            }
        }
        catch(SocketTimeoutException ste){
            //if client does not respond to server in the alloted time, close connection and restart server
            try{
                System.out.println("Client response exceeded timeout value: " + connection.getSoTimeout() / 1000 + " s\n\nDisconnecting from client and restarting server...\n");
                closeStuff();
            }
            catch(SocketException se){
                System.out.println("Connection to client was reset.\n\nRestarting server...");
            }
            catch(IOException e2){
                System.out.println(e2);
            }
            start(port);
        }
        catch(SocketException se){
            //if the connection to the client is reset (probably because the client terminated the connection), restart the server
            System.out.println("Connection to client was reset.\n\nRestarting server...\n");
            try{
                closeStuff();
            }
            catch(IOException e2){
                System.out.println(e2);
            }
            start(port);
        }
        catch(IOException e){
            System.out.println("\n" + e + "\n");
            try{
                closeStuff();
            }
            catch(IOException e2){
                System.out.println(e2);
            }
            start(port);
        }
        catch(NoSuchAlgorithmException nsae){
            System.out.println("\n" + nsae + "\n");
            try{
                closeStuff();
            }
            catch(IOException e2){
                System.out.println(e2);
            }
            start(port);
        }
        catch(IllegalArgumentException iae){
            //if the user enters the wrong port number, inform them on what to do
            System.out.println("\n\nPlease use a valid port number or 0 for the first open port!\nType help or -h or --help or /? for help");
            System.exit(1);
        }
    }

    //method for sending the file to the client
    private void sendWholeFile(File file) throws IOException, NoSuchAlgorithmException{
        System.out.println("Sending file " + file.getName() + " size: " + makeByteValueReadable(file.length()) + "\n");
        fileInput = new FileInputStream(file);
        long fileLength = file.length();
        output.writeLong(fileLength); //send the file size to the client
        byte[] fileBytes = new byte[BUFFERSIZE]; //send the file in BUFFERSIZE byte chuncks of data
        int bufferCheck = 0;
        double percentageDone = 0.0;
        MessageDigest md = MessageDigest.getInstance("MD5");
        checksum = getChecksum(file, md).substring(0, 32);
        while((bufferCheck = fileInput.read(fileBytes)) > 0){ //send the file in BUFFERSIZE byte chunks while updating the user on the progress
            output.write(fileBytes, 0, bufferCheck);
            output.flush();
            percentageDone += bufferCheck;
            if((int) ((percentageDone/fileLength)*100) == 100){
                System.out.print("File sent: " + (int) ((percentageDone/fileLength)*100) + "%\r");
                System.out.println("\n");
                break;
            }
            if( (int) ((percentageDone/fileLength)*100) > (int) (((percentageDone-bufferCheck)/fileLength)*100)){
                System.out.print("File sent: " + (int) ((percentageDone/fileLength)*100) + "%\r");
            }
            
        }
        output.flush();
        System.out.println("File sent successfully!\n");
        System.out.println("Calculated Checksum value: " + checksum + "\n");
    }

    //method to generate the MD5 checksum for the selected file
    private String getChecksum(File file, MessageDigest md) throws IOException{
        DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md);
        byte[] buffer = new byte[BUFFERSIZE];
        int count = 0;
        while((count = dis.read(buffer)) != -1){
            md.update(buffer, 0, count);
        }
        dis.close();
        // bytes to hex
        StringBuilder result = new StringBuilder();
        for (byte b : md.digest()) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    //method to close all open streams and sockets
    private void closeStuff() throws IOException{
        System.out.println("Closing streams...\n");
        input.close();
        if(fileInput != null){
            fileInput.close();
        }
        if(fileOutput != null){
            fileOutput.flush();
            fileOutput.close();
        }
        output.flush();
        output.close();
        System.out.println("Closing connection...\n");
        connection.close();
        server.close();
        System.out.println("Connection closed successfully!");
    }

    //method to get all files in the directroy 'AvailableFiles' and store them in a list
    private ArrayList<File> getAvailableFileList() throws IOException{
        ArrayList<File> availableFiles = new ArrayList<File>();
        File[] files = new File("AvailableFiles//").listFiles();
        for(File f : files){
            if(f.isFile()){
                availableFiles.add(f);
            }
        }
        return availableFiles;

        
    }

    //method to recieve from the client which file to send and check if it's in 'AvailableFiles'
    private Boolean getFileFromClient(ArrayList<File> fileList) throws IOException{
        String file = input.readUTF();
        fileToSend = file;
        for(File f : fileList){
            if(f.getName().equals(file)){
                return true;
            }
        }
        return false;
    }

    //Converts bytes value to more readable format (KB, MB, GB, TB, PB)
    private String makeByteValueReadable(long b){
        if(b/Math.pow(10, 15) > 1){
            return b/Math.pow(10, 15) + " PB";
        }else if(b/Math.pow(10, 12) > 1){
            return b/Math.pow(10, 12) + " TB";
        }else if(b/Math.pow(10, 9) > 1){
            return b/Math.pow(10, 9) + " GB";
        }else if(b/Math.pow(10, 6) > 1){
            return b/Math.pow(10, 6) + " MB";
        }else if(b/Math.pow(10, 3) > 1){
            return b/Math.pow(10, 3) + " KB";
        }else{
            return b + " B";
        }
    }

    //Method to find all the interfaces with IPv4 addresses and print them out so the user knows which IP to give to the client
    private void findAllInterfaceIP4() throws SocketException{
        System.out.println();
        Enumeration en = NetworkInterface.getNetworkInterfaces();
        while(en.hasMoreElements()){
            NetworkInterface ni=(NetworkInterface) en.nextElement();
            Enumeration ee = ni.getInetAddresses();
            if(ee.hasMoreElements()){
                InetAddress ia= (InetAddress) ee.nextElement();
                if(ia.getHostAddress().contains(".")){
                    System.out.println(ni.getName() + ": ");
                    System.out.println(ia.getHostAddress());
                }
                while(ee.hasMoreElements()) {
                    ia = (InetAddress) ee.nextElement();
                    if(ia.getHostAddress().contains(".")){
                        System.out.println(ia.getHostAddress());
                    }
                }
                //System.out.println();
            }
                
        } 
    }

    public static void main(String[] args){
        //if the user types help, show the help message
        //Otherwise error check and run the program
        try{
            if(args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("/?")){
                System.out.println("\nUsage: java Server <port>\n\nport can be any open valid port or 0 for the first open available port");
                System.out.println("This server program remains running until it is manually killed with ^C (Ctrl+C)");
                System.out.println("If 0 is used for the port number, then after closing connections with a client a new port is used (the old port is still closed)");
                System.out.println("This program pulls files from a directory named 'AvailableFiles' within the same directory that this class file is in");
            }else{
                Server server = new Server(Integer.parseInt(args[0]));
            }
        }
        catch(NumberFormatException nfe){
            System.out.println("\n\nPlease use a valid port number or 0 for the first open port!\n\nType help or -h or --help or /? for help");
            System.exit(1);
        }
        catch(ArrayIndexOutOfBoundsException aioobe){
            System.out.println("\nPlease provide arguments to use this program\n\nUsage: java Server <port>");
        }
    } 
}