package backup_service;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import backup_service.protocols.*;

import java.io.IOException;



public class Server implements IBackup {
	
    private int id;
    
    private ChannelManager channelManager;
    
    
    public Server(String[] args) throws IOException{
    	channelManager = new ChannelManager(args);
    }

    
    public int getId() {
		return id;
	}


	public ChannelManager getChannelManager() {
		return channelManager;
	}



	public void test(){
    	try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	MDB mdb = channelManager.getMDB();
    	try {
			mdb.sendMessage(MessageConstructor.getPUTCHUNK("THIS_IS_THE_FILE_ID_BRO_255BYTESTHIS_IS_THE_FILE_ID_BRO_255BYTES", 255, 3, new byte[]{1,2,3}));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }
    
    @Override
    public void backup(String file_id, String rep_degree) {

    }

    @Override
    public void delete(String file_id) {

    }

    @Override
    public void restore(String file_id) {

    }

    @Override
    public void reclaim(String space) {

    }

    @Override
    public String state() {
        return null;
    }
    
    public static void main(String args[]){
        
        String remote_object_name = args[5];
        Server sv;
        try {
        	sv = new Server(args);
            IBackup peer = (IBackup) UnicastRemoteObject.exportObject(sv,0);
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(remote_object_name,peer);
            System.out.println("RMI ready!");
            sv.test();
        } catch(IOException e){
        	System.err.println("Error Initializing Server: " + e.toString());
        	e.printStackTrace();
        } catch (Exception e) {
            System.err.println("RMI failed: " + e.toString());
            e.printStackTrace();
        }
        
    }
}
//Chunk -> (fileID,chunkNum) max size : 64KBytes (64000Bytes)
/*
Backup a file
Restore a file
Delete a file
Manage local service storage
Retrieve local service state information
*/
/* Protocolos
* MC:Port MDB:Port MDR:Port Protocol_Version ServerID ServiceAccessPoint
Messages
Header
<MessageType> <Version> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF>

1. Chunk backup
PUTCHUNK <Version> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body> (para o MDB)
STORED <Version> <SenderId> <FileId> <ChunkNo> <CRLF><CRLF> (para o MC)

2. chunk restore
GETCHUNK <Version> <SenderId> <FileId> <ChunkNo> <CRLF><CRLF> (para o MC)
CHUNK <Version> <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body> (para o MDR)

3. file deletion
DELETE <Version> <SenderId> <FileId> <CRLF><CRLF> (para o MC)

4. space reclaiming
REMOVED <Version> <SenderId> <FileId> <ChunkNo> <CRLF><CRLF> (para o MC)

*/
