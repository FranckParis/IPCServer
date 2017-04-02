import javax.net.ssl.SSLSocket;
import java.io.*;
import java.math.BigInteger;
//import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

/**
 * Created by franck on 3/6/17.
 */
public class Connection {

    //Attributes
    private SSLSocket socket;
    private PrintWriter output;
    private BufferedReader input;
    private String state;
    private String connectedUser;
    private String ts;

    //Constructor
    public Connection (SSLSocket socket){

        this.socket = socket;
        try {
            this.input = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.output = new PrintWriter(this.socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Connection instantiation error.");
        }
    }

    //Methods
    public void run(){

        //Connection
        this.connect();

        //Transaction
        this.transaction();
    }

    public void connect (){

        //Connection ready
        System.out.println("New connection to "+ socket.getInetAddress() + " : " + socket.getLocalPort());
        this.ts = System.currentTimeMillis()+ "@"+ socket.getInetAddress();
        output.println("+OK POP3 Server Ready <"+this.ts+">");
        this.send();

        //Authorization
        this.state = "authorization";
        int comptFails = 0;
        boolean quit = false;
        boolean matchFound = false;
        boolean userFound = false;

        //User file access
        File file = new File("src/Users.txt");
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        StringBuffer stringBuffer = new StringBuffer();
        String line;

        //Connection variables
        String username = "";
        String password = "";
        String saltedPassword = "";

        //Authentication loop
        while(comptFails < 3 && !matchFound){
            System.out.println("Login : ");
            try {
                String login = input.readLine();
                switch (login.substring(0, 4)){
                    //APOP
                    case "APOP" :
                        matchFound = false;
                        username = login.split("\\s+")[1];
                        password = login.split("\\s+")[2];

                        //Reading file to find user
                        while ((line = bufferedReader.readLine()) != null && !matchFound) {
                            if(line.equals("STARTUSER")) {
                                line = bufferedReader.readLine();
                                if (line.startsWith(username + "/")){
                                    try {
                                        saltedPassword = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("md5").digest((ts+(line.split("/")[1])).getBytes())));
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    }
                                    if (saltedPassword.equals(password)){
                                        matchFound = true;
                                    }
                                }
                            }
                        }
                        if(!matchFound){
                            comptFails++;
                            System.out.println("Connection attempt failed : "+ comptFails);
                            output.println("-ERR POP3 No Such User here : "+ username);
                            this.send();

                            try {
                                fileReader = new FileReader(file);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            bufferedReader = new BufferedReader(fileReader);
                            stringBuffer = new StringBuffer();
                        }
                        break;

                    //USER then PASS
                    case "USER" :
                        String userLine = "";
                        userFound = false;

                        username = login.split("\\s+")[1];
                        System.out.println("Username : "+ username);

                        while ((line = bufferedReader.readLine()) != null && !userFound && this.state.equals("authorization")) {
                            if(line.equals("STARTUSER")) {
                                line = bufferedReader.readLine();
                                System.out.println("File user : "+line.split("/")[0]);
                                if (line.split("/")[0].equals(username)) {
                                    userFound = true;
                                    this.state = "pass";
                                    userLine = line;
                                    System.out.println("Connection attempt with username "+username);
                                    output.println("+OK POP3 User Found");
                                    this.send();
                                }
                            }
                        }
                        if(!userFound){
                            comptFails++;
                            System.out.println("Connection attempt failed (User not Found) : "+ comptFails);
                            output.println("-ERR POP3 No Such User here");
                            this.send();

                            try {
                                fileReader = new FileReader(file);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            bufferedReader = new BufferedReader(fileReader);
                            stringBuffer = new StringBuffer();
                        }

                        if(this.state.equals("pass")){
                            System.out.println("Password :");
                            String pass = input.readLine().split("\\s+")[1];

                            System.out.println("pass : "+pass);
                            System.out.println("line : "+userLine);

                            try {
                                saltedPassword = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("md5").digest((ts+(userLine.split("/")[1])).getBytes())));
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }

                            if (saltedPassword.equals(pass)) {
                                matchFound = true;
                            }
                            else{
                                comptFails++;
                                System.out.println("User " + username + " : Wrong Password.");
                                output.println("-ERR POP3 Authentication Failed");
                                this.send();
                                this.state = "authorization";
                                try {
                                    fileReader = new FileReader(file);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                                bufferedReader = new BufferedReader(fileReader);
                                stringBuffer = new StringBuffer();
                            }
                        }
                        break;

                    //QUIT
                    case "QUIT" :
                        quit = true;
                        comptFails = 3;
                    break;

                    //Default
                    default :
                        System.out.println("Unknown command. Please login first.");
                        output.println("-ERR POP3 Login Required.");
                        this.send();
                    break;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //Connection success
        if(matchFound){
            System.out.println("User " + username + " successfully connected to server.");
            this.connectedUser = username;
            output.println("+OK POP3 Authentication Success");
            this.send();
            this.state = "transaction";
        }
        //Connection failed
        else{
            if(quit){
                System.out.println("Disconnecting from server.");
            }
            else{
                System.out.println("Connection failed : 3 attempts failed.");
            }
            output.println("+OK POP3 Server signing off");
            this.send();
            this.state = "closed";
        }
    }

    public void transaction(){
        if (this.state.equals("transaction")) {

            //User file access
            File file = new File("src/mail/"+ this.connectedUser + ".txt");
            FileReader fileReader = null;
            ArrayList <Integer> listToDelete = new ArrayList<>();

            while(this.state.equals("transaction")){

                try {
                    fileReader = new FileReader(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                StringBuffer stringBuffer = new StringBuffer();
                String line;

                try {
                    String query = input.readLine();
                    System.out.println("Query : " + query);

                    switch (query.substring(0, 4)) {
                        case "STAT" :
                            int size = 0;
                            int nbMails = 0;

                            //Reading file to count mails
                            while ((line = bufferedReader.readLine()) != null) {
                                if(line.equals("----")) {
                                    nbMails++;
                                    for(int i = 0; i<5; i++){
                                        bufferedReader.readLine();
                                    }
                                    String mailText = "";
                                    while(!(line = bufferedReader.readLine()).equals(".")){
                                        mailText += line;
                                    }
                                    size += mailText.getBytes().length;
                                }
                            }
                            System.out.println("STAT done for user : "+ this.connectedUser);
                            output.println("+OK "+ nbMails+" "+size);
                            this.send();
                            break;

                        case "LIST" :
                            ArrayList <Integer> listMails = new ArrayList<>();
                            ArrayList <Integer> idMails = new ArrayList<>();

                            //Reading file to count mails
                            while ((line = bufferedReader.readLine()) != null) {
                                if(line.equals("----")) {

                                    for(int i = 0; i<4; i++){
                                        bufferedReader.readLine();
                                    }
                                    line = bufferedReader.readLine();
                                    idMails.add(Integer.parseInt(line.split(" ")[1]));

                                    String mailText = "";
                                    while(!(line = bufferedReader.readLine()).equals(".")){
                                        mailText += line;
                                    }
                                    listMails.add(mailText.getBytes().length);
                                }
                            }

                            System.out.println("LIST done for user : "+ this.connectedUser);
                            output.println("+OK "+ listMails.size());
                            for (int i = 0; i<listMails.size(); i++){
                                output.println(idMails.get(i) +" "+ listMails.get(i));
                            }
                            this.send();
                        break;

                        case "RETR" :
                            int id = Integer.parseInt(query.split(" ")[1]);
                            int cpt = 1;
                            int sizeRetrieve = 0;
                            boolean messageFound = false;
                            String mailData ="";

                            //Reading file to find mail
                            while ((line = bufferedReader.readLine()) != null && !messageFound) {
                                mailData="";
                                if(line.equals("----")) {
                                    while(!(line = bufferedReader.readLine()).equals(".")){
                                        if(cpt == 5){
                                            if(Integer.parseInt(line.split(" ")[1]) == id){
                                                messageFound = true;
                                            }
                                        }
                                        if (cpt > 6){
                                            sizeRetrieve += line.getBytes().length;
                                        }
                                        mailData += line;
                                        mailData += "\r\n";
                                        cpt++;
                                    }
                                    cpt = 1;
                                }
                            }
                            if(messageFound){
                                System.out.println("RETRIEVE "+id+" done for user : "+ this.connectedUser);
                                output.println("+OK "+ sizeRetrieve + "\r\n" + mailData);
                                this.send();
                                listToDelete.add(id);
                            }
                            else{
                                System.out.println("RETRIEVE "+id+" failed for user : "+ this.connectedUser);
                                output.println("-ERR POP3 Message not found");
                                this.send();
                            }
                        break;

                        case "QUIT" :
                            System.out.println("Disconnecting from server.");
                            output.println("+OK POP3 Server signing off");
                            this.send();
                            this.state = "closed";
                            this.update(listToDelete);
                        break;

                        //Default
                        default :
                            System.out.println("Unknown command.");
                            output.println("-ERR POP3 Unavailable Command.");
                            this.send();
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void send(){
        this.output.println("EOS");
        this.output.flush();
    }

    public void update(ArrayList<Integer> list){
        System.out.println("Messages to delete : ");
        for(int i = 0; i<list.size(); i++){
            System.out.println(list.get(i));
        }
    }
}
