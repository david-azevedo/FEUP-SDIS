package backup_service.distributor.services;

import backup_service.distributor.IDistribute;
import backup_service.protocols.ChannelManager;
import backup_service.protocols.HeaderInfo;
import file_managment.FileManager;
import utils.Debug;

public class RestoreChunk implements IDistribute {
	
	private FileManager fileManager;
	private ChannelManager channelManager;
	private HeaderInfo header;
	
	public RestoreChunk(ChannelManager channelManager, FileManager fileManager){
		this.fileManager = fileManager;
		this.channelManager = channelManager;
	}
	
	
	@Override
	public boolean distribute(String line) {//CHUNK
		
		header = new HeaderInfo(line);


		if(!header.version.equals(ChannelManager.getVersion()))
			return false;

		if(header.senderID == ChannelManager.getServerID())
			return false;
		
		Debug.log(1,"CHUNK","Received[" + header.chunkNo + "]" + header.fileID + " From Server:" + header.senderID);
		if(channelManager != null)
			this.channelManager.getMDR().expectBody=true;  
		return true;
	}

	@Override
	public void distribute(byte[] data) {
		// TODO Chunk DATA!
		//Save Data

		Debug.log(2,"CHUNK"," ChunkSize " + data.length);
		Debug.log(2,"CHUNK"," ChunkSize " + header.toString());
		
		fileManager.getChunkManager().AddChunk(header.fileID, header.chunkNo, data);
		
	}

}
