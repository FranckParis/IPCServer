import java.io.*;
import java.net.Socket;


/**
 * Created by menros on 19/03/17.
 */
public class Client {
    //Attributes
    private Socket clientSocket;
    private PrintWriter output;
    private BufferedReader input;
    private String state;
    private String hostName;
    private int port;
    private int nbAttempts;

    //Constructors
    public Client (String hostName, int port){
        this.hostName = hostName;
        this.port = port;
        this.state = "";
        this.nbAttempts = 0;
    }

    //Methods
    private void run(){
        try {
            String sentence;
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

            this.clientSocket = new Socket("localhost", 3586);
            this.output = new PrintWriter(this.clientSocket.getOutputStream());
            this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            this.read();

            this.state = "authorisation";

        while(!this.state.equals("stopped")){
            switch(this.state){
                case "authorisation" :
                    System.out.println("Please connect by APOP or USER :");
                    sentence = inFromUser.readLine();
                    this.write(sentence);
                    this.read();
                break;

                case "connection" :
                    System.out.println("Enter your password :");
                    sentence = inFromUser.readLine();
                    this.write(sentence);
                    this.read();
                break;

                case "transaction" :
                    System.out.println("Type the command you need :");
                    sentence = inFromUser.readLine();
                    this.write(sentence);
                    this.read();
                break;

                default :
                    System.out.println("Error. Unknown State. Shuting down");
                    this.write("QUIT");
                    this.read();
                break;
            }
        }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Stop
    public void stop(){
        System.out.println("Closing...");
        try {
            this.clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Closed");
    }

    //update the status
    private Boolean updateStatus(String line){
        Boolean update = true;
        switch(line){
            case "+OK POP3 Authentication Success" :
                System.out.println("Connection Success");
                this.state = "transaction";
            break;

            case "+OK POP3 User Found" :
                System.out.println("User Found");
                this.state = "connection";
            break;

            case "+OK POP3 Server signing off" :
                if(this.nbAttempts >= 3){
                    System.out.println("Error : Too many attempts");
                }
                System.out.println("Server signing off");
                this.state = "stopped";
            break;

            case "-ERR POP3 Authentication Failed" :
                this.nbAttempts++;
                System.out.println("Authentication Failed : " + this.nbAttempts + " attempts failed");
                this.state = "authorisation";
            break;

            default:
                if(line.contains("-ERR POP3 No Such User here")){
                    this.nbAttempts++;
                    System.out.println("User not found : " + this.nbAttempts + " attempts failed");
                    this.state = "authorisation";
                }
                else if(line.contains("-ERR POP3"))
                    System.out.println("ERROR Unavailable Command");
                else
                    update = false;
            break;
        }
        return update;
    }

    // display the input or update the state
    private void read(){
        String line = "";
        Boolean update = false;
        try {
            line = this.input.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(line.contains("+OK POP3") || line.contains("-ERR POP3")){
            update = updateStatus(line);
        }
        if(!update) System.out.println(line);
        try {
            while (!(line = this.input.readLine()).equals("EOS")){
                if(!update) System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(String sentence){
        System.out.println(sentence);
        this.output.println(sentence);
        this.output.flush();
    }

    //Main
    public static void main (String[] args){
        Client cli = new Client("localhost", 3586);
        cli.run();
        cli.stop();
    }
}
