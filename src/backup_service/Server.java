package backup_service;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import backup_service.distributor.Distributor;
import backup_service.distributor.IDistribute;
import backup_service.distributor.services.*;
import backup_service.protocols.*;
import file_managment.*;
import utils.Debug;
import utils.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Server implements IBackup {
	
    private int id;
    
    public static final int MAX_RESEND = 5;
    private ChannelManager channelManager;
    private Distributor[] distributors = new Distributor[3];
    private FileManager fileManager = null;
    private Services services = null;
    private DeleteScheduler deleteScheduler = null;
    
    public Server(String[] args) throws IOException, NoSuchAlgorithmException{
    	
    	fileManager = new FileManager("Backup!"+args[1]);
    	
    	distributors[0] = new Distributor();
    	distributors[1] = new Distributor();
    	distributors[2] = new Distributor();
    	
    	channelManager = new ChannelManager(args, distributors);
    	Debug.log(0,"SERVER","[" + ChannelManager.getServerID() + "]");
    	services = new Services(channelManager,fileManager);
  
    	distributors[0].addDistributor("STORED", new Stored(fileManager));
    	if(ChannelManager.getVersion().equals("1.0")){
    		Debug.log(1,"Enhancements","DISABLED");
    		distributors[0].addDistributor("GETCHUNK", new Restore(channelManager, fileManager));
			distributors[0].addDistributor("DELETE", new DeleteFile(fileManager));
    	}else{
    		Debug.log(1,"Enhancements","ENABLED");
    		distributors[0].addDistributor("GETCHUNK", new Restore2(channelManager, fileManager));
			distributors[0].addDistributor("DELETE", new DeleteFile2(channelManager,fileManager));
			this.deleteScheduler = new DeleteScheduler(channelManager,fileManager);
			new DeleteConfirmationReceiver(channelManager.getMC().getPort() + ChannelManager.getServerID(), fileManager).start();
			new TCPReceiver(channelManager.getMDR().getPort() + ChannelManager.getServerID(), fileManager).start();
    	}

    	distributors[0].addDistributor("REMOVED", new RemoveChunk(channelManager, fileManager));

    	IDistribute PUTCHUNK = new SaveChunk(channelManager, fileManager);
    	distributors[1].addDistributor("PUTCHUNK", PUTCHUNK);
    	distributors[1].addDistributor("DATA", PUTCHUNK);
    	
    	
    	IDistribute CHUNK = new RestoreChunk(channelManager,fileManager);
    	distributors[2].addDistributor("CHUNK", CHUNK);
    	distributors[2].addDistributor("DATA", CHUNK);
    	
    	//distributors[2].addDistributor("RESTORE", service);
		if(!ChannelManager.getVersion().equals("1.0"))
			deleteScheduler.start();
		back_to_backup();

    }

	private void back_to_backup() {

    	FileInProgress fip;
		while((fip = fileManager.get_next_file_in_progress()) != null){

			if(fip.is_a_path()){

				backup(fip.get_name(),fip.getRep_degree());

			} else {

				byte[] fileData = fileManager.get_file_chunk(fip.get_name(),fip.getChunk_no());
				if(fileData == null) {
					fileManager.remove_file_in_progress(fip.get_name());
					continue;
				}

				try {
					services.sendPutChunk(fip.get_name(), fip.getChunk_no(), fip.getRep_degree(), fileData,true);
				} catch (IOException e) {
					Debug.log("BACK_TO_BACKUP","Failed to backup up!");
					e.printStackTrace();
				}

				fileManager.remove_file_in_progress(fip.get_name());
			}
		}
	}

	public int getId() {
		return id;
	}


	public ChannelManager getChannelManager() {
		return channelManager;
	}
	
	@Override
    public void backup(String file_path, int rep_degree) {
    	Debug.log("BACKUP PATH:", file_path);

    	fileManager.add_file_in_progress(file_path,rep_degree);

    	try {
			FileStreamInformation fs = fileManager.get_chunks_from_file(file_path,rep_degree);
			byte[] chunkData = new byte[FileManager.chunk_size_bytes];
			
			int chunkNo = 0;
			while(true){
				
				if(fs.getStream().available() == 0){ //Was a pair
					services.sendPutChunk(fs.getFileID(), chunkNo, rep_degree, new byte[]{});
					break;
				}
				
				int size = fs.getStream().read(chunkData);
				byte[] data =  Arrays.copyOf(chunkData, size);
	
				services.sendPutChunk(fs.getFileID(), chunkNo, rep_degree, data);

				if(size < FileManager.chunk_size_bytes){
					break;
				}
				chunkNo++;
			}

			//Quando faz backup de um ficheiro guardar a info do my_files
			fileManager.save_my_files();
			fs.getStream().close();
			
		} catch (IOException e) {
			//RETURN MESSAGE TO THE CLIENT TELLING SOMETHING IS WRONG!
			e.printStackTrace();
		}

		fileManager.remove_file_in_progress(file_path);
    	
    }

    @Override
    public void delete(String path) {

    	if(ChannelManager.getVersion().equals("1.0")){
    		delete_1(path);
		} else {
    		delete_2(path);
		}
    }


	public void delete_1(String path) {
		try {
			String file_id = fileManager.delete_my_file(path);
			if(file_id == null){//Não é um ficheiro meu!
				Debug.log(1,"DELETE","UnknownFile! " + path);
				return;
			}
			Debug.log(1,"DELETE","Sending DELETE! " + file_id);
			channelManager.getMC().sendMessage(MessageConstructor.getDELETE(file_id));

		} catch (IOException e) {
		}
	}

	public void delete_2(String path) {


		String file_id = fileManager.get_file_id(path);

		if(file_id == null){//Não é um ficheiro meu!
			Debug.log(1,"DELETE","UnknownFile! " + path);
			return;
		}

		fileManager.add_deleted_file_entry(file_id);

		fileManager.delete_my_file(path);

		deleteScheduler.wake();
	}

    @Override
    public void restore(String file_path) {
    	FileOutputStream fs;
    	String file_id = fileManager.get_file_id(file_path);

    	if(file_id == null){
    		return;
		}

		try {
			fs = fileManager.createFile(file_path);
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
    	
    	FilePartitioned filePart = fileManager.getChunkManager().ListenToFile(file_id);
    	
    	int chunkCounter = 0;
    	while(true){
    		try {
				
    			this.channelManager.getMC().sendMessage(MessageConstructor.getGETCHUNK(file_id, chunkCounter));
				Debug.log("Sent GETCHUNK" + chunkCounter);
				Thread.sleep(600);
				
				byte[] chunk = filePart.getChunk(chunkCounter);
				if(chunk != null){
					chunkCounter++;
					fs.write(chunk);//Escrita da chunk no ficheiro
					if(filePart.totalChunks() > -1)
						if(chunkCounter > filePart.totalChunks())
							break;
				}else{
					Debug.log("Error Receiving CHUNK Retrieving!");
				}
			} catch (Exception e) {
				Debug.log("SERVER","RESTORE");
				e.printStackTrace();
			}
    	}
    	try {
			fs.close();
		} catch (IOException e) {
			Debug.log("SERVER","RESTORE");
			e.printStackTrace();
		}
    	fileManager.getChunkManager().StopListen(file_id);
    	
    }
    
    @Override
    public void reclaim(int space) {
    	File directory = fileManager.setDisk_size(space);
    	Debug.log("RECLAIM", "NewSPACE " + space + "KB");
    	Debug.log("RECLAIM", "DIRECTORY SIZE" + directory.length() + "KB");
    	
    	while(FileManager.getFolderSize(directory) > fileManager.getDisk_size() * 1000){
             FileChunk delete = fileManager.getMapper().get_chunk_to_delete();

             if(!fileManager.delete_file_chunk(delete.getFile_id(),delete.getN_chunk(),true))
                 Debug.log("ERROR", "Could not delete file! " + delete.toString());
             
             try {
				this.channelManager.getMC().sendMessage(MessageConstructor.getREMOVED(delete.getFile_id(), delete.getN_chunk()));
				Thread.sleep(500);
	            
			} catch (Exception e) {
				Debug.log("SERVER","reclaim");
				e.printStackTrace();
			}
         }
    }

    @Override
    public String state() {
		/*Retrieve local service state information
		This operation allows to observe the service state. In response to such a request,
		the peer shall send to the client the following information:

			For each file whose backup it has initiated:
				The file pathname
				The backup service id of the file
				The desired replication degree
				For each chunk of the file:
					Its id
					Its perceived replication degree

			For each chunk it stores:
				Its id
				Its size (in KBytes)
				Its perceived replication degree

			The peer's storage capacity, i.e. the maximum amount of disk space that can be
			used to store chunks, and the amount of storage (both in KBytes) used to backup the chunks.
*/
		return fileManager.toString();
    }
    
    public static void main(String args[]){

		String remote_object_name = args[2];
        Server sv;

        if(!Utilities.check_commands(args)){
        	return;
		}

		try {
        	sv = new Server(args);
			IBackup peer = (IBackup) UnicastRemoteObject.exportObject(sv,0);
			Registry registry = LocateRegistry.getRegistry();
			registry.rebind(remote_object_name,peer);
            System.out.println("RMI ready!");
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

Protocolos
	MC:Port MDB:Port MDR:Port Protocol_Version ServerID ServiceAccessPoint
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

