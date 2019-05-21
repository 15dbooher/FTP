/*  Daniel Booher
    djb150230
    Client class for the FTP
*/
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;

public class Client{

    private Socket connection;
    private DataInputStream fileInput;
    private DataInputStream input;
    private DataOutputStream output;
    private FileOutputStream fileOutput;
    private Scanner terminalInput = new Scanner(System.in);
    private String checksum = "";
    private final int BUFFERSIZE = 8192;
    private File file;

    public Client(String address, int port){

        try{ //Open a socket and try to connect to server
            connection = new Socket(address, port);
            System.out.println("\nConnected to server " + connection.getInetAddress().getHostAddress() + " on server port " + connection.getPort() + "...");
            connection.setSoTimeout(1000 * 60); //set a timeout delay for 1 minute
            fileInput = new DataInputStream(connection.getInputStream());
            input = new DataInputStream(connection.getInputStream());
            output = new DataOutputStream(connection.getOutputStream());
            
            //recieve a list of files available to download from server
            String numberOfFiles = input.readUTF();
            System.out.println("\n" + numberOfFiles + "\n");
            int nof = Integer.parseInt(numberOfFiles.substring(17));
            String line = "";
            for(int i = 0; i < nof; i++){
                line = input.readUTF();
                System.out.println(line + "\n");
            }

            //Select file to request
            System.out.println("Which file to download?\n");
            String fileToDownload = terminalInput.nextLine();
            //ask server for the file to send
            System.out.println("\nRequesting " + fileToDownload + " to download...\n");
            output.writeUTF(fileToDownload);

            //Listen for server to say if it has the file or not
            String serverResponse = "";
            serverResponse = input.readUTF();
            if(serverResponse.equals("FF")){ //File Found
                file = new File("DownloadedFiles//" + fileToDownload);
                recieveWholeFile(file);
                //tell Server eof has been reached
                output.writeUTF("EOFT");
                //recieve the Server's checksum
                String serverChecksum = "";
                serverChecksum = input.readUTF();
                System.out.println("Calculated Checksum value: " + checksum + "\n");
                if(serverChecksum.contains("CS=")){
                    System.out.println("Server Checksum: " + serverChecksum.substring(3) + "\n");
                    if(serverChecksum.substring(3).equals(checksum)){
                        //if the checksums match, tell the server and close the connection
                        System.out.println("Checksum values matched!\n");
                        output.writeUTF("CSACK");
                    }
                    else{ //if the checksum values do not match, tell the server, delete the file, and close the connection
                        System.out.println("Checksum values did not match\n");
                        output.writeUTF("CSNACK");
                        file.delete();
                    }
                }
                else{
                    System.out.println("Failed to recieve checksum from server...\n");
                    output.writeUTF("CSNACK");
                }
            }
            else if(serverResponse.equals("FNF")){ //File Not Found
                System.out.println("Requested file not available\n");
            }
        
            closeStuff();

        }
        catch(SocketTimeoutException ste){
            try{
                System.out.println("Client response exceeded timeout value: " + connection.getSoTimeout() / 1000 + " s\n\nDisconnecting from client and restarting server...\n");
            }
            catch(SocketException se){
                System.out.println(se);
            }
        }
        catch(SocketException se){ //if connection to server was reset (probably by the server terminating the connection), close the connection
            System.out.println("Connection to server was reset.\n\nClosing connection...\n");
            if(!file.equals(null)){
                file.delete();
            }
        }
        catch(NoSuchElementException nsee){ //if the user manually terminates the program while waiting for user input, inform the user and clost the connection
            System.out.println("Program was closed while waiting for terminal input.\n\nPlease put some input next time...");
            if(!file.equals(null)){
                file.delete();
            }
        }
        catch(IOException e){
            System.out.println(e);
        }
        catch(NoSuchAlgorithmException nsae){
            System.out.println(nsae);
        }

    }

    //Method to recieve the selected file from the server
    private void recieveWholeFile(File file) throws IOException, NoSuchAlgorithmException{
        System.out.println("Downloading file...\n");
        fileOutput = new FileOutputStream(file);
        long fileLength = input.readLong();
        System.out.println("Server file " + file.getName() + " size: " + makeByteValueReadable(fileLength) + "\n");
        byte[] fileBytes = new byte[BUFFERSIZE]; //recieve the file in BUFFERSIZE byte chunks
        int bufferCheck = 0;
        double percentageDone = 0.0;
        while((bufferCheck = input.read(fileBytes)) > 0){ //while the file is downloading, update the user
            fileOutput.write(fileBytes, 0, bufferCheck);
            fileOutput.flush();
            percentageDone += bufferCheck;
            if((int) ((percentageDone/fileLength)*100) == 100){
                //Once the file is downloaded, generate the checksum for the file
                MessageDigest md = MessageDigest.getInstance("MD5");
                checksum = getChecksum(file, md).substring(0, 32);
                System.out.println("File download: " + (int) ((percentageDone/fileLength)*100) + "%");
                System.out.println();
                break;
            }
            if( (int) ((percentageDone/fileLength)*100) > (int) (((percentageDone-bufferCheck)/fileLength)*100)){
                System.out.print("File download: " + (int) ((percentageDone/fileLength)*100) + "%\r");
            }
            
        }
        fileOutput.flush();
        System.out.println("Downloaded file " + file.getName() + " size: " + makeByteValueReadable(fileLength) + "\n");
        System.out.println("Download successfull!\n");
    }

    //Method that generates the checksum for the selected file
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

    //method to close all open streams and sockets
    private void closeStuff() throws IOException{
        System.out.println("Closing streams...\n");
        input.close();
        output.flush();
        output.close();
        if(fileOutput != null){
            fileOutput.flush();
            fileOutput.close();
        }
        if(fileInput != null){
            fileInput.close();
        }
        System.out.println("Closing connection...\n");
        connection.close();
        System.out.println("Connection closed successfully!");
    }

    public static void main(String[] args){
        try{
            if(args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("/?")){
                //if the user types help, show the help message
                //Otherwise error check and run the program
                System.out.println("\nUsage: java Client <IP Address> <port>\n       java Client <Host> <port>\n");
                System.out.println("IP Address must be a valid IPv4 Address\nHost must be a valid evaluatable Hostname\nport can be any open valid port");
                System.out.println("This program downloads files and saves them into a directory named 'DownloadedFiles' that is within the same directory that this class file is in");
            }else{
                //try{
                    Client client = new Client(args[0], Integer.parseInt(args[1]));
                }
        }
        catch(NumberFormatException nfe){
            System.out.println("\n\nPlease use a valid port number!\nType help or -h or --help or /? for help");
            System.exit(1);
        }
        catch(ArrayIndexOutOfBoundsException aioobe){
            System.out.println("\nPlease provide the correct number of arguments to use this program");
            System.out.println("\nUsage: java Client <IP Address> <port>\n       java Client <Host> <port>\n");
        }
    }    
}