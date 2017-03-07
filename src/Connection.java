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

        //Connection ready
        System.out.println("New connection to "+ socket.getInetAddress() + " : " + socket.getLocalPort());
        output.println("+OK POP3 Server Ready");
        output.flush();

        //Authorization
        this.state = "authorization";
        int comptFails = 0;
        boolean matchFound = false;
        boolean userFound = false;

        //User file access
        File file = new File("src/Data.txt");
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
                            output.println("-ERR POP3 No Such User here");
                            output.flush();
                        }
                        break;

                    //USER then PASS
                    case "USER" :
                        username = login.split("\\s+")[1];
                        System.out.println("Username : "+ username);

                        while ((line = bufferedReader.readLine()) != null && !userFound && this.state.equals("authorization")) {
                            if(line.equals("STARTUSER")) {
                                line = bufferedReader.readLine();
                                System.out.println("File user : "+line.split("/")[0]);
                                if (line.split("/")[0].equals(username)) {
                                    userFound = true;
                                    this.state = "pass";
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
                        }

                        if(this.state.equals("pass")){
                            System.out.println("Password :");
                            String pass = input.readLine().split("\\s+")[1];
                            if (line.split("/")[1].equals(pass)) {
                                matchFound = true;
                            }
                            else{
                                comptFails++;
                                System.out.println("User " + username + " : Wrong Password.");
                                output.println("-ERR POP3 Authentication Failed");
                                output.flush();
                                this.state = "authorization";
                            }
                        }
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //Connection success
        if(matchFound){
            System.out.println("User " + username + " successfully connected to server.");
            output.println("+OK POP3 Authentication Success");
            output.flush();
            this.state = "transaction";
        }
        //Connection failed
        else{
            System.out.println("Connection failed : 3 attempts failed.");
            output.println("+OK POP3 Server signing off");
            output.flush();
            this.state = "closed";
        }
    }
}
