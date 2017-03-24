import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.util.ArrayList;

import static java.lang.System.exit;

/**
 * Created by franck on 3/6/17.
 */
public class Server {

    //Attributes
    private SSLServerSocket serverSocket;

    //Constructors
    public Server (int port){
        try{
            SSLServerSocketFactory fact = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            this.serverSocket =(SSLServerSocket) fact.createServerSocket(port);

            String[] supportedCiphers = serverSocket.getSupportedCipherSuites();
            ArrayList<String> enabledCiphers = new ArrayList<>();

            //Checking ciphers
            for (String s : supportedCiphers) {
                if(s.contains("anon")){
                    enabledCiphers.add(s);
                }
            }

            String[] ciphersToSet = new String[enabledCiphers.size()];
            ciphersToSet = enabledCiphers.toArray(ciphersToSet);

            this.serverSocket.setEnabledCipherSuites(ciphersToSet);

            //this.serverSocket = new ServerSocket(port);
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
                SSLSocket socket = (SSLSocket) serverSocket.accept();

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
