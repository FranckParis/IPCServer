import java.io.*;
import java.net.Socket;

/**
 * Created by franck on 3/6/17.
 */
public class Connection {

    //Attributes
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private String state;
    private String connectedUser;

    //Constructor
    public Connection (Socket socket){
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
        output.println("+OK POP3 Server Ready");
        output.flush();

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

        //Authentication loop
        while(comptFails < 3 && !matchFound){
            System.out.println("Login : ");
            try {
                String login = input.readLine();
                switch (login.substring(0, 4)){

                    //APOP
                    case "APOP" :
                        username = login.split("\\s+")[1];
                        password = login.split("\\s+")[2];

                        //Reading file to find user
                        while ((line = bufferedReader.readLine()) != null && !matchFound) {
                            if(line.equals("STARTUSER")) {
                                line = bufferedReader.readLine();
                                if (line.equals(username + "/" + password)) {
                                    matchFound = true;
                                }
                            }
                        }
                        if(!matchFound){
                            comptFails++;
                            System.out.println("Connection attempt failed : "+ comptFails);
                            output.println("-ERR POP3 No Such User here : "+ username);
                            output.flush();

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
                                    output.flush();
                                }
                            }
                        }
                        if(!userFound){
                            comptFails++;
                            System.out.println("Connection attempt failed (User not Found) : "+ comptFails);
                            output.println("-ERR POP3 No Such User here");
                            output.flush();

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

                            if (userLine.split("/")[1].equals(pass)) {
                                matchFound = true;
                            }
                            else{
                                comptFails++;
                                System.out.println("User " + username + " : Wrong Password.");
                                output.println("-ERR POP3 Authentication Failed");
                                output.flush();
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
            output.flush();
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
            output.flush();
            this.state = "closed";
        }
    }

    public void transaction(){
        if (this.state.equals("transaction")) {

            //User file access
            File file = new File("src/mail/"+ this.connectedUser + ".txt");
            FileReader fileReader = null;
            try {
                fileReader = new FileReader(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuffer stringBuffer = new StringBuffer();
            String line;

            while(this.state.equals("transaction")){
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
                                    while((line = bufferedReader.readLine()) != null){
                                        if(line.equals(".\r\n")){
                                            System.out.println(" = . found");
                                        }
                                        mailText += line;
                                        mailText += "\n";
                                    }
                                    System.out.println(mailText);
                                }
                            }
                            System.out.println("STAT done for user : "+ this.connectedUser);
                            output.println("+OK "+ nbMails+" "+size);
                            output.flush();
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
