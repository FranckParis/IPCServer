import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static java.lang.System.exit;

/**
 * Created by franck on 3/6/17.
 */
public class Server {

    //Attributes
    private ServerSocket serverSocket;

    //Constructors
    public Server (int port){
        try{
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("Socket initialisation error. Please check network config.");
            e.printStackTrace();
            exit(-3);
        }
    }

    //Methods
    private void run(){
        System.out.println("Server listening on port "+serverSocket.getLocalPort());
        while(true){
            try {
                //Waiting for connection
                Socket socket = serverSocket.accept();

                //Connection detected
                Connection c = new Connection(socket);
                (new Thread(() -> c.run())).start();

            }catch (IOException e) {
                e.printStackTrace();
                System.out.println("Connection error.");
            }
        }
    }

    //Main
    public static void main (String[] args){
            Server srv = new Server(3586);
            srv.run();
        }
    }
